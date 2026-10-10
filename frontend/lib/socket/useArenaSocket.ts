'use client';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

export type ArenaState = { connected: boolean; phase: string; question: any; roster: any[]; reveal: any; leaderboard: any[]; result: any[]; snapshot: any; error: string | null };
export function useArenaSocket(roomCode: string) {
  const [state, setState] = useState<ArenaState>({ connected: false, phase: 'LOBBY', question: null, roster: [], reveal: null, leaderboard: [], result: [], snapshot: null, error: null });
  const clientRef = useRef<Client | null>(null);
  const callbacks = useRef<{ [key: string]: StompSubscription }>({});
  const room = roomCode.toUpperCase();
  const readCredentials = useCallback(() => {
    if (typeof window === 'undefined') return null;
    const playerId = sessionStorage.getItem('playerId'); const token = sessionStorage.getItem('sessionToken');
    return playerId && token ? { playerId, token } : null;
  }, []);
  const publish = useCallback((destination: string, body: unknown = {}) => {
    const client = clientRef.current;
    if (client?.connected) client.publish({ destination, body: JSON.stringify(body) });
  }, []);
  useEffect(() => {
    const credentials = readCredentials();
    if (!credentials) return;
    const base = process.env.NEXT_PUBLIC_WS_URL || (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws-arena';
    const url = new URL(base); url.searchParams.set('playerId', credentials.playerId); url.searchParams.set('token', credentials.token);
    const client = new Client({ brokerURL: url.toString(), reconnectDelay: 500, maxReconnectDelay: 15000,
      heartbeatIncoming: 10000, heartbeatOutgoing: 10000, connectionTimeout: 10000,
      onConnect: () => {
        setState(s => ({ ...s, connected: true, error: null }));
        const subscribe = (key: string, destination: string, field: keyof ArenaState) => {
          callbacks.current[key]?.unsubscribe();
          callbacks.current[key] = client.subscribe(destination, (message: IMessage) => {
            const value = JSON.parse(message.body);
            setState(s => ({ ...s, [field]: value, ...(field === 'snapshot' ? { phase: value.phase } : {}), ...(field === 'roster' ? {} : {}) }));
          });
        };
        subscribe('state', `/topic/room/${room}/state`, 'snapshot');
        subscribe('roster', `/topic/room/${room}/roster`, 'roster');
        subscribe('question', `/user/queue/question`, 'question');
        subscribe('joined', `/user/queue/joined`, 'snapshot');
        subscribe('reconnect', `/user/queue/reconnect-state`, 'snapshot');
        subscribe('answer-result', `/user/queue/answer-result`, 'snapshot');
        subscribe('reveal', `/topic/room/${room}/reveal`, 'reveal');
        subscribe('leaderboard', `/topic/room/${room}/leaderboard`, 'leaderboard');
        subscribe('result', `/topic/room/${room}/results`, 'result');
        publish(`/app/room/${room}/join`);
        publish(`/app/room/${room}/reconnect`);
      },
      onDisconnect: () => setState(s => ({ ...s, connected: false })),
      onWebSocketClose: () => setState(s => ({ ...s, connected: false })),
      onStompError: frame => setState(s => ({ ...s, error: frame.headers.message || 'WebSocket error' }))
    });
    clientRef.current = client; client.activate();
    return () => { Object.values(callbacks.current).forEach(sub => sub.unsubscribe()); callbacks.current = {}; void client.deactivate(); clientRef.current = null; };
  }, [room, readCredentials, publish]);
  const actions = useMemo(() => ({ start: () => publish(`/app/room/${room}/start`), answer: (questionId: string, optionId: string) => publish(`/app/room/${room}/answer`, { questionId, optionId }) }), [publish, room]);
  return { ...state, ...actions };
}
