package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quidoitquoi.api.dto.GroupLogResponse;
import com.quidoitquoi.api.exceptions.ForbiddenOperationException;
import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.GroupLog;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.ExpenseRepository;
import com.quidoitquoi.api.repository.GroupLogRepository;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.GroupAuthorizationService;
import com.quidoitquoi.api.services.GroupService;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupLogRepository groupLogRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final ExpenseRepository expenseRepository;
    private final GroupAuthorizationService groupAuthorizationService;

    public GroupServiceImpl(
            GroupRepository groupRepository,
            GroupLogRepository groupLogRepository,
            UserRepository userRepository,
            MemberRepository memberRepository,
            ExpenseRepository expenseRepository,
            GroupAuthorizationService groupAuthorizationService) {
        this.groupRepository = groupRepository;
        this.groupLogRepository = groupLogRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.groupAuthorizationService = groupAuthorizationService;
    }

    @Override
    public List<Group> getGroupsByUserId(UUID userId, UUID authenticatedUserId) {
        if (!userId.equals(authenticatedUserId)) {
            throw new ForbiddenOperationException();
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return groupRepository.findDistinctByAdmin_IdOrMembers_User_Id(userId, userId);
    }

    @Override
    public Optional<Group> getGroupById(UUID id, UUID authenticatedUserId) {
        return Optional.of(groupAuthorizationService.requireMember(id, authenticatedUserId));
    }

    @Override
    public Page<GroupLogResponse> getGroupLogs(
            UUID groupId,
            int page,
            int size,
            UUID authenticatedUserId) {
        groupAuthorizationService.requireMember(groupId, authenticatedUserId);

        PageRequest pageable = PageRequest.of(
                page,
                Math.min(size, MAX_LOG_PAGE_SIZE),
                Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
        return groupLogRepository.findByGroup_Id(groupId, pageable)
                .map(log -> new GroupLogResponse(
                        log.getId(),
                        log.getType(),
                        log.getDate(),
                        log.getMadeBy().getId(),
                        log.getMadeBy().getUsername()));
    }

    @Override
    public List<Settlement> calculateSettlements(UUID groupId, UUID authenticatedUserId) {
        groupAuthorizationService.requireMember(groupId, authenticatedUserId);

        List<Member> members = memberRepository.findByGroup_Id(groupId);
        if (members.isEmpty()) {
            return List.of();
        }

        // A positive balance means the member should receive money;
        // a negative balance means the member owes money.
        Map<UUID, Long> balances = new LinkedHashMap<>();
        Map<UUID, Member> membersById = new LinkedHashMap<>();
        for (Member member : members) {
            balances.put(member.getId(), 0L);
            membersById.put(member.getId(), member);
        }

        // Sum expenses by payer. Expenses with no valid payer are ignored.
        long total = 0L;
        for (Expense expense : expenseRepository.findByGroup_Id(groupId)) {
            if (expense.getAmount() == null || expense.getPaidBy() == null || !balances.containsKey(expense.getPaidBy().getId())) {
                continue;
            }
            total += expense.getAmount();
            UUID payerId = expense.getPaidBy().getId();
            balances.put(payerId, balances.get(payerId) + expense.getAmount());
        }

        // Every member pays an equal share of the total expenses.
        // When the division has a remainder, the first members receive one
        // extra smallest currency unit so that all amounts still balance.
        long equalShare = total / members.size();
        long remainder = total % members.size();
        for (int index = 0; index < members.size(); index++) {
            UUID memberId = members.get(index).getId();
            long share = equalShare + (index < remainder ? 1 : 0);
            balances.put(memberId, balances.get(memberId) - share);
        }

        // Convert net balances into separate lists of people who owe money
        // and people who need to be reimbursed.
        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();
        for (Map.Entry<UUID, Long> balance : balances.entrySet()) {
            if (balance.getValue() < 0) {
                debtors.add(new Balance(balance.getKey(), -balance.getValue()));
            } else if (balance.getValue() > 0) {
                creditors.add(new Balance(balance.getKey(), balance.getValue()));
            }
        }

        // Match debtors and creditors until one side is fully settled.
        // Each transfer uses the largest amount possible for the current pair.
        List<Settlement> settlements = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Balance debtor = debtors.get(debtorIndex);
            Balance creditor = creditors.get(creditorIndex);
            long amount = Math.min(debtor.amount(), creditor.amount());
            Member from = membersById.get(debtor.memberId());
            Member to = membersById.get(creditor.memberId());
            settlements.add(new Settlement(
                    from.getId(), from.getName(), to.getId(), to.getName(), amount));

            debtors.set(debtorIndex, new Balance(debtor.memberId(), debtor.amount() - amount));
            creditors.set(creditorIndex, new Balance(creditor.memberId(), creditor.amount() - amount));
            if (debtors.get(debtorIndex).amount() == 0) {
                debtorIndex++;
            }
            if (creditors.get(creditorIndex).amount() == 0) {
                creditorIndex++;
            }
        }
        return settlements;
    }

    private record Balance(UUID memberId, long amount) {
    }

    @Override
    public Group createGroup(UUID userId, Group group, UUID authenticatedUserId) {
        if (!userId.equals(authenticatedUserId)) {
            throw new ForbiddenOperationException();
        }
        var admin = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        group.setAdmin(admin);
        Group savedGroup = groupRepository.save(group);

        Member adminMember = new Member(admin.getUsername(), savedGroup);
        adminMember.setUser(admin);
        memberRepository.save(adminMember);

        return savedGroup;
    }

    @Override
    @Transactional
    public Optional<Group> updateGroup(UUID id, Group group, UUID madeByUserId) {
        Group existingGroup = groupAuthorizationService.requireAdmin(id, madeByUserId);
        boolean changed = !Objects.equals(existingGroup.getName(), group.getName())
                || !Objects.equals(existingGroup.getDescription(), group.getDescription())
                || !Objects.equals(existingGroup.getImg(), group.getImg())
                || !Objects.equals(existingGroup.getCurrency(), group.getCurrency());
        User madeBy = changed
                ? userRepository.findById(madeByUserId)
                        .orElseThrow(() -> new UserNotFoundException(madeByUserId))
                : null;

        existingGroup.setName(group.getName());
        existingGroup.setDescription(group.getDescription());
        existingGroup.setImg(group.getImg());
        existingGroup.setCurrency(group.getCurrency());
        Group savedGroup = groupRepository.save(existingGroup);
        if (changed) {
            groupLogRepository.save(new GroupLog(GroupLog.GROUP_UPDATED, savedGroup, madeBy));
        }
        return Optional.of(savedGroup);
    }

    @Override
    public boolean deleteGroup(UUID id, UUID authenticatedUserId) {
        Group group = groupAuthorizationService.requireAdmin(id, authenticatedUserId);
        groupRepository.delete(group);
        return true;
    }
}