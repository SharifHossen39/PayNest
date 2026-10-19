package com.paynest.transfer.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.transfer.application.TransferService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/transfers")
public class TransferController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody TransferRequest request
    ) {
        TransferResponse response = transferService.transfer(user.id(), request);
        LOGGER.info("event=transfer outcome=success");
        return response;
    }
}
