package com.paynest.wallet.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.wallet.application.WalletQueryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletQueryService walletQueryService;

    public WalletController(WalletQueryService walletQueryService) {
        this.walletQueryService = walletQueryService;
    }

    @GetMapping
    public WalletResponse currentWallet(@AuthenticationPrincipal AuthenticatedUser user) {
        return walletQueryService.currentWallet(user.id());
    }
}
