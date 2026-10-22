package com.example.wallet;

import com.example.wallet.repository.WalletRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerTest {

    @Autowired MockMvc mvc;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository transactionRepository;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    private long createWallet(String name, String email, String balance) throws Exception {
        String body = mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"initialBalance\":%s}".formatted(name, email, balance)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"walletId\":(\\d+).*", "$1"));
    }

    @Test
    void fullFlowAndErrorFormat() throws Exception {
        long a = createWallet("Rahul", "rahul@example.com", "1000.00");
        long b = createWallet("Priya", "priya@example.com", "500.00");

        mvc.perform(post("/api/wallets/" + a + "/deposit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousBalance", is(1000.00)))
                .andExpect(jsonPath("$.currentBalance", is(1500.00)));

        mvc.perform(post("/api/wallets/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromWalletId\":%d,\"toWalletId\":%d,\"amount\":300.00}".formatted(a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.transactionId").exists());

        mvc.perform(get("/api/wallets/" + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(1200.00)))
                .andExpect(jsonPath("$.email", is("rahul@example.com")));

        mvc.perform(get("/api/wallets/" + a + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type", is("TRANSFER_OUT")))
                .andExpect(jsonPath("$[1].type", is("DEPOSIT")));

        mvc.perform(get("/api/wallets/" + b + "/transactions"))
                .andExpect(jsonPath("$[0].type", is("TRANSFER_IN")));
    }

    @Test
    void insufficientBalanceReturns400WithErrorCode() throws Exception {
        long a = createWallet("A", "a@example.com", "10.00");
        long b = createWallet("B", "b@example.com", "0.00");
        mvc.perform(post("/api/wallets/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromWalletId\":%d,\"toWalletId\":%d,\"amount\":50.00}".formatted(a, b)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INSUFFICIENT_BALANCE")))
                .andExpect(jsonPath("$.message", is("Insufficient balance to complete the transfer")));
    }

    @Test
    void errorMappings() throws Exception {
        long a = createWallet("A", "a@example.com", "10.00");

        mvc.perform(get("/api/wallets/9999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("WALLET_NOT_FOUND")));
        mvc.perform(get("/api/wallets/9999/transactions")).andExpect(status().isNotFound());
        mvc.perform(get("/api/wallets/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_PARAMETER")));

        mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"a@example.com\",\"initialBalance\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_EMAIL")));

        mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"initialBalance\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details", hasSize(3)));

        mvc.perform(post("/api/wallets/" + a + "/deposit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DEPOSIT_AMOUNT")));

        mvc.perform(post("/api/wallets/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromWalletId\":%d,\"toWalletId\":%d,\"amount\":1.00}".formatted(a, a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("SAME_WALLET_TRANSFER")));

        mvc.perform(post("/api/wallets/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromWalletId\":%d,\"toWalletId\":%d,\"amount\":-1.00}".formatted(a, a + 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_TRANSFER_AMOUNT")));

        mvc.perform(post("/api/wallets/transfer").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("MALFORMED_REQUEST")))
                .andExpect(jsonPath("$.message", not(containsString("Exception"))));
    }
}
