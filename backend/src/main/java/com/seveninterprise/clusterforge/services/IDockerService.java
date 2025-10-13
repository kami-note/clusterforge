/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */

package com.seveninterprise.clusterforge.services;

import java.util.ArrayList;

/**
 *
 * @author levi
 */
public interface IDockerService {
    public ArrayList<String> listContainers();                             // e.g. ["my_minecraft_server_1", "my_minecraft_server_2"]
    public ArrayList<String> listTemplates();                              // e.g. ["minecraft_server_1.15.4", "minecraft_server_1.16.5"]
    public String getDockerVersion();                                       // e.g. "Docker version 20.10.7, build f0df350"
    public void createContainer(String templateName, String containerName); // e.g. "minecraft_server_1.15.4", "my_minecraft_server_1" 
    public void startContainer(String containerName);                       // e.g. "my_minecraft_server_1"
    public void stopContainer(String containerName);                        // e.g. "my_minecraft_server_1"
    public void removeContainer(String containerName);                      // e.g. "my_minecraft_server_1"
    public String runCommand(String command);                               // Executa comando docker/docker-compose
}
