package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.dto.RetailerStatementResponse;
import com.vyapaaros.retailer.service.RetailerStatementService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retailer/me")
public class RetailerStatementController {
    private final RetailerStatementService statements;

    public RetailerStatementController(RetailerStatementService statements) { this.statements = statements; }

    @GetMapping("/distributors")
    public RetailerStatementService.DistributorSummary currentDistributor(@RequestParam UUID retailerId,
        Authentication authentication) {
        return statements.currentDistributor(authentication, retailerId);
    }

    @GetMapping("/distributors/{distributorId}/statement")
    public RetailerStatementResponse statement(@PathVariable UUID distributorId,
        @RequestParam UUID retailerId, @RequestParam(required = false) LocalDate fromDate,
        @RequestParam(required = false) LocalDate toDate, Authentication authentication) {
        return statements.statement(authentication, retailerId, distributorId, fromDate, toDate);
    }
}
