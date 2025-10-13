/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.seveninterprise.clusterforge.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seveninterprise.clusterforge.services.KernalService;

/**
 *
 * @author levi
 */

@RestController
public class KernalController implements IKernalController {

    private final KernalService kernalService;

    public KernalController(KernalService kernalService) {
        this.kernalService = kernalService;
    }

    @GetMapping("/runcommand")
    @Override
    public String runCommand(@RequestParam String command) {
        return kernalService.runCommand(command);
    }
}
