/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.seveninterprise.clusterforge.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.stereotype.Service;
/**
 *
 * @author levi
 */

@Service
public class KernalService implements IKernalService {

    @Override
    public String runCommand(String command) {
        StringBuilder output = new StringBuilder();

        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            output.append("\nProcess exited with code: ").append(exitCode);

        } catch (IOException | InterruptedException e) {
            // Melhor logar em vez de só printar stacktrace
            return "Error executing command: " + e.getMessage();
        }

        return output.toString();
    }

    @Override
    public String getOSInfo() {
        return System.getProperty("os.name") + " - " + System.getProperty("os.version");
    }
}