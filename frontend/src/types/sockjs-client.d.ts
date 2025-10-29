/**
 * Declaração de tipos para sockjs-client
 * Este módulo não possui tipos TypeScript nativos
 */
declare module 'sockjs-client' {
  export default class SockJS {
    constructor(url: string);
    close(): void;
    send(data: string): void;
    onopen: ((event: Event) => void) | null;
    onclose: ((event: CloseEvent) => void) | null;
    onmessage: ((event: MessageEvent) => void) | null;
    onerror: ((event: Event) => void) | null;
    readyState: number;
  }
}

