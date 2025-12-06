package com.kryptforge.clusterforge.clusters.dto;

import java.util.List;
import java.util.Map;

import com.kryptforge.clusterforge.clusters.ClusterService.ClusterParams;

/**
 * DTO para requisição de atualização de parâmetros do cluster.
 */
public record ClusterUpdateParamsRequest(
        Map<String, String> env,
        List<Integer> ports,
        List<String> volumes,
        Integer cpuLimitPercent,
        Long memoryLimit,
        Integer diskLimit,
        Integer networkLimit) {
    public ClusterParams toParams() {
        return new ClusterParams(env, ports, volumes, cpuLimitPercent, memoryLimit, diskLimit, networkLimit);
    }
}
