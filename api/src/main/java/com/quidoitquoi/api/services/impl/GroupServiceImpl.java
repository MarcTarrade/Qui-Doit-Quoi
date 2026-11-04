package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

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
import com.quidoitquoi.api.services.GroupImageStorageService;
import com.quidoitquoi.api.services.GroupService;

@Service
public class GroupServiceImpl implements GroupService {

    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);

    private final GroupRepository groupRepository;
    private final GroupLogRepository groupLogRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final ExpenseRepository expenseRepository;
    private final GroupAuthorizationService groupAuthorizationService;
    private final GroupImageStorageService groupImageStorageService;

    public GroupServiceImpl(
            GroupRepository groupRepository,
            GroupLogRepository groupLogRepository,
            UserRepository userRepository,
            MemberRepository memberRepository,
            ExpenseRepository expenseRepository,
            GroupAuthorizationService groupAuthorizationService,
            GroupImageStorageService groupImageStorageService) {
        this.groupRepository = groupRepository;
        this.groupLogRepository = groupLogRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.groupAuthorizationService = groupAuthorizationService;
        this.groupImageStorageService = groupImageStorageService;
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

        List<Member> members = memberRepository.findByGroup_Id(groupId).stream()
                .sorted(Comparator.comparing(Member::getId))
                .toList();
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

        // Credit each payer and charge each selected participant their equal share.
        for (Expense expense : expenseRepository.findByGroup_Id(groupId)) {
            if (expense.getAmount() == null || expense.getPaidBy() == null || !balances.containsKey(expense.getPaidBy().getId())) {
                continue;
            }
            UUID payerId = expense.getPaidBy().getId();
            balances.put(payerId, balances.get(payerId) + expense.getAmount());

            List<Member> participants = expense.getParticipants();
            if (participants == null || participants.isEmpty()) {
                participants = members;
            } else {
                participants = participants.stream()
                        .filter(member -> member != null && balances.containsKey(member.getId()))
                        .sorted(Comparator.comparing(Member::getId))
                        .toList();
            }
            if (participants.isEmpty()) {
                continue;
            }
            long equalShare = expense.getAmount() / participants.size();
            long remainder = expense.getAmount() % participants.size();
            for (int index = 0; index < participants.size(); index++) {
                UUID memberId = participants.get(index).getId();
                long share = equalShare + (index < remainder ? 1 : 0);
                balances.put(memberId, balances.get(memberId) - share);
            }
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
    @Transactional
    public Group updateGroupImage(UUID id, MultipartFile upload, UUID madeByUserId) {
        Group group = groupAuthorizationService.requireAdmin(id, madeByUserId);
        UUID newImageId = groupImageStorageService.store(id, upload);
        Optional<UUID> previousImageId =
                groupImageStorageService.imageIdFromReference(id, group.getImg());

        group.setImg(groupImageStorageService.reference(id, newImageId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    UUID imageToDelete = status == STATUS_COMMITTED
                            ? previousImageId.orElse(null)
                            : newImageId;
                    if (imageToDelete == null) {
                        return;
                    }
                    try {
                        groupImageStorageService.delete(id, imageToDelete);
                    } catch (IOException exception) {
                        logger.error("Unable to clean up group image {} for group {}", imageToDelete, id, exception);
                    }
                }
            });
        }

        Group savedGroup = groupRepository.save(group);
        User madeBy = userRepository.findById(madeByUserId)
                .orElseThrow(() -> new UserNotFoundException(madeByUserId));
        groupLogRepository.save(new GroupLog(GroupLog.GROUP_UPDATED, savedGroup, madeBy));
        return savedGroup;
    }

    @Override
    public boolean deleteGroup(UUID id, UUID authenticatedUserId) {
        Group group = groupAuthorizationService.requireAdmin(id, authenticatedUserId);
        Optional<UUID> imageId = groupImageStorageService.imageIdFromReference(id, group.getImg());
        groupRepository.delete(group);
        if (imageId.isPresent()) {
            try {
                groupImageStorageService.delete(id, imageId.get());
            } catch (IOException exception) {
                logger.error("Unable to clean up image for deleted group {}", id, exception);
            }
        }
        return true;
    }
}