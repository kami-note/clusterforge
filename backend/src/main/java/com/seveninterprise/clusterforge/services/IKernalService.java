/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */

package com.seveninterprise.clusterforge.services;

/**
 *
 * @author levi
 */
public interface IKernalService {
    public String runCommand(String command); // e.g. "ls -la"
    public String getOSInfo();                // e.g. "Ubuntu 20.04.2 LTS"
}
