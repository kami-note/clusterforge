/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */

package com.seveninterprise.clusterforge.controllers;

import org.springframework.web.bind.annotation.PathVariable;

/**
 *
 * @author levi
 */
public interface IKernalController {
    String runCommand(@PathVariable String command);
}
