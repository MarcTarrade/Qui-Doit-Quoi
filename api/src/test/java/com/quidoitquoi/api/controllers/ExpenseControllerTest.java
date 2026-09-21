package com.quidoitquoi.api.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.quidoitquoi.api.exceptions.GlobalExceptionHandler;
import com.quidoitquoi.api.models.Category;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.ExpenseService;

@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EXPENSE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private ExpenseService expenseService;

    private MockMvc mockMvc;
    private Expense expense;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExpenseController(expenseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        User user = new User("jdoe", "john.doe@example.com", null, "Doe", "John");
        Group group = new Group("Trip", "Shared trip expenses", null, "EUR", user);
        Member member = new Member("John", group);
        expense = new Expense("Dinner", 4500L, Category.FOOD, member, group);
    }

    @Test
    void getExpensesByGroupIdReturnsExpenses() throws Exception {
        when(expenseService.getExpensesByGroupId(GROUP_ID)).thenReturn(List.of(expense));

        mockMvc.perform(get("/api/groups/{groupId}/expenses", GROUP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Dinner"))
                .andExpect(jsonPath("$[0].amount").value(4500))
                .andExpect(jsonPath("$[0].category").value("FOOD"));
    }

    @Test
    void getExpensesByGroupIdReturnsNotFoundWhenGroupDoesNotExist() throws Exception {
        when(expenseService.getExpensesByGroupId(GROUP_ID))
                .thenThrow(new com.quidoitquoi.api.exceptions.GroupNotFoundException(GROUP_ID));

        mockMvc.perform(get("/api/groups/{groupId}/expenses", GROUP_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getExpenseByIdReturnsExpense() throws Exception {
        when(expenseService.getExpenseById(EXPENSE_ID)).thenReturn(Optional.of(expense));

        mockMvc.perform(get("/api/expenses/{id}", EXPENSE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Dinner"))
                .andExpect(jsonPath("$.amount").value(4500));
    }

    @Test
    void getExpenseByIdReturnsNotFoundWhenExpenseDoesNotExist() throws Exception {
        when(expenseService.getExpenseById(EXPENSE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/expenses/{id}", EXPENSE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString(EXPENSE_ID.toString())));
    }

    @Test
    void getExpenseByIdReturnsBadRequestForInvalidId() throws Exception {
        mockMvc.perform(get("/api/expenses/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void addExpenseReturnsCreatedExpenseAndLocation() throws Exception {
        when(expenseService.addExpense(eq(GROUP_ID), any(Expense.class))).thenReturn(expense);

        mockMvc.perform(post("/api/groups/{groupId}/expenses", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/expenses/null"))
                .andExpect(jsonPath("$.description").value("Dinner"))
                .andExpect(jsonPath("$.category").value("FOOD"));
    }

    @Test
    void addExpenseRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "",
                                  "amount": -1,
                                  "category": "FOOD",
                                  "paidBy": {
                                    "id": "%s"
                                  }
                                }
                                """.formatted(MEMBER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateExpenseReturnsUpdatedExpense() throws Exception {
        when(expenseService.updateExpense(eq(EXPENSE_ID), any(Expense.class)))
                .thenReturn(Optional.of(expense));

        mockMvc.perform(put("/api/expenses/{id}", EXPENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Dinner"))
                .andExpect(jsonPath("$.amount").value(4500));
    }

    @Test
    void updateExpenseReturnsNotFoundWhenExpenseDoesNotExist() throws Exception {
        when(expenseService.updateExpense(eq(EXPENSE_ID), any(Expense.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/expenses/{id}", EXPENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteExpenseReturnsNoContent() throws Exception {
        when(expenseService.deleteExpense(EXPENSE_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/expenses/{id}", EXPENSE_ID))
                .andExpect(status().isNoContent());

        verify(expenseService).deleteExpense(EXPENSE_ID);
    }

    @Test
    void deleteExpenseReturnsNotFoundWhenExpenseDoesNotExist() throws Exception {
        when(expenseService.deleteExpense(EXPENSE_ID)).thenReturn(false);

        mockMvc.perform(delete("/api/expenses/{id}", EXPENSE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String expenseJson() {
        return """
                {
                  "description": "Dinner",
                  "amount": 4500,
                  "category": "FOOD",
                  "paidBy": {
                    "id": "%s"
                  }
                }
                """.formatted(MEMBER_ID);
    }
}
