/**
 * Utilitários de validação
 */

import { VALIDATION_LIMITS } from '@/constants';
import { ClusterData } from '@/types';

/**
 * Valida nome de cluster
 */
export function validateClusterName(name: string): { valid: boolean; error?: string } {
  if (!name.trim()) {
    return { valid: false, error: 'O nome do cluster é obrigatório' };
  }
  if (name.trim().length < 3) {
    return { valid: false, error: 'O nome deve ter pelo menos 3 caracteres' };
  }
  if (name.length > 100) {
    return { valid: false, error: 'O nome deve ter no máximo 100 caracteres' };
  }
  return { valid: true };
}

/**
 * Valida alocação de CPU
 */
export function validateCpu(cpu: number): { valid: boolean; error?: string } {
  if (cpu < VALIDATION_LIMITS.CPU.min || cpu > VALIDATION_LIMITS.CPU.max) {
    return {
      valid: false,
      error: `A alocação de CPU deve estar entre ${VALIDATION_LIMITS.CPU.min}% e ${VALIDATION_LIMITS.CPU.max}%`,
    };
  }
  return { valid: true };
}

/**
 * Valida alocação de RAM
 */
export function validateRam(ram: number): { valid: boolean; error?: string } {
  if (ram < VALIDATION_LIMITS.RAM.min || ram > VALIDATION_LIMITS.RAM.max) {
    return {
      valid: false,
      error: `A alocação de RAM deve estar entre ${VALIDATION_LIMITS.RAM.min}GB e ${VALIDATION_LIMITS.RAM.max}GB`,
    };
  }
  return { valid: true };
}

/**
 * Valida alocação de disco
 */
export function validateDisk(disk: number): { valid: boolean; error?: string } {
  if (disk < VALIDATION_LIMITS.DISK.min || disk > VALIDATION_LIMITS.DISK.max) {
    return {
      valid: false,
      error: `A alocação de disco deve estar entre ${VALIDATION_LIMITS.DISK.min}GB e ${VALIDATION_LIMITS.DISK.max}GB`,
    };
  }
  return { valid: true };
}

/**
 * Valida porta personalizada
 */
export function validatePort(port: string): { valid: boolean; error?: string } {
  if (!port) return { valid: true };
  
  const portNum = Number(port);
  if (isNaN(portNum) || portNum < VALIDATION_LIMITS.PORT.min || portNum > VALIDATION_LIMITS.PORT.max) {
    return {
      valid: false,
      error: `A porta deve ser um número entre ${VALIDATION_LIMITS.PORT.min} e ${VALIDATION_LIMITS.PORT.max}`,
    };
  }
  return { valid: true };
}

/**
 * Valida dados completos do cluster
 */
export function validateClusterData(data: ClusterData): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  const nameValidation = validateClusterName(data.name);
  if (!nameValidation.valid) {
    errors.push(nameValidation.error!);
  }
  
  if (!data.service) {
    errors.push('Um tipo de serviço deve ser selecionado');
  }
  
  const cpuValidation = validateCpu(data.resources.cpu);
  if (!cpuValidation.valid) {
    errors.push(cpuValidation.error!);
  }
  
  const ramValidation = validateRam(data.resources.ram);
  if (!ramValidation.valid) {
    errors.push(ramValidation.error!);
  }
  
  const diskValidation = validateDisk(data.resources.disk);
  if (!diskValidation.valid) {
    errors.push(diskValidation.error!);
  }
  
  if (data.port) {
    const portValidation = validatePort(data.port);
    if (!portValidation.valid) {
      errors.push(portValidation.error!);
    }
  }
  
  return {
    valid: errors.length === 0,
    errors,
  };
}


