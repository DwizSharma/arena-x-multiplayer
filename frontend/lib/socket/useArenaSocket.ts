'use client';
import { Client, IMessage, ReconnectionTimeMode, StompSubscription } from '@stomp/stompjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

export type ArenaState = { connected: boolean; phase: string; question: any; roster: any[]; progress: any; reveal: any; leaderboard: any[]; result: any[]; snapshot: any; answerResult: any; error: string | null };
export function useArenaSocket(roomCode: string, anonymousView = false) {
  const [state, setState] = useState<ArenaState>({ connected: false, phase: 'LOBBY', question: null, roster: [], progress: null, reveal: null, leaderboard: [], result: [], snapshot: null, answerResult: null, error: null });
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
    let cancelled = false;
    let activeClient: Client | null = null;
    const boot = async () => {
    let credentials = readCredentials();
    if (!credentials && anonymousView) {
      try {
        const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL || ''}/api/session`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: `Projector_${room}`, college: 'ArenaX Projector' }) });
        if (response.ok) credentials = await response.json();
      } catch { setState(s => ({ ...s, error: 'Unable to connect to the ArenaX server' })); }
    }
    if (!credentials || cancelled) return;
    const base = process.env.NEXT_PUBLIC_WS_URL || (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws-arena';
    const url = new URL(base); url.searchParams.set('playerId', credentials.playerId); url.searchParams.set('token', credentials.token);
    const client = new Client({ brokerURL: url.toString(), reconnectDelay: 500, maxReconnectDelay: 15000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 10000, heartbeatOutgoing: 10000, connectionTimeout: 10000,
      onConnect: () => {
        setState(s => ({ ...s, connected: true, error: null }));
        const subscribe = (key: string, destination: string, field: keyof ArenaState) => {
          callbacks.current[key]?.unsubscribe();
          callbacks.current[key] = client.subscribe(destination, (message: IMessage) => {
            const value = JSON.parse(message.body);
            setState(s => ({ ...s, [field]: value, ...(field === 'snapshot' ? { phase: value.phase, question: value.currentQuestion || null } : {}), ...(field === 'roster' ? {} : {}) }));
          });
        };
        subscribe('state', `/topic/room/${room}/state`, 'snapshot');
        subscribe('roster', `/topic/room/${room}/roster`, 'roster');
        subscribe('progress', `/topic/room/${room}/progress`, 'progress');
        subscribe('question', `/user/queue/question`, 'question');
        if (anonymousView) subscribe('public-question', `/topic/room/${room}/question`, 'question');
        subscribe('joined', `/user/queue/joined`, 'snapshot');
        subscribe('reconnect', `/user/queue/reconnect-state`, 'snapshot');
        subscribe('answer-result', `/user/queue/answer-result`, 'answerResult');
        subscribe('reveal', `/topic/room/${room}/reveal`, 'reveal');
        subscribe('leaderboard', `/topic/room/${room}/leaderboard`, 'leaderboard');
        subscribe('result', `/topic/room/${room}/results`, 'result');
        if (anonymousView) publish(`/app/room/${room}/watch`);
        else {
          publish(`/app/room/${room}/join`);
          publish(`/app/room/${room}/reconnect`);
        }
      },
      onDisconnect: () => setState(s => ({ ...s, connected: false })),
      onWebSocketClose: () => setState(s => ({ ...s, connected: false })),
      onStompError: frame => setState(s => ({ ...s, error: frame.headers.message || 'WebSocket error' }))
    });
    if (cancelled) return;
    activeClient = client; clientRef.current = client; client.activate();
    };
    void boot();
    return () => { cancelled = true; Object.values(callbacks.current).forEach(sub => sub.unsubscribe()); callbacks.current = {}; if (activeClient) void activeClient.deactivate(); if (clientRef.current === activeClient) clientRef.current = null; };
  }, [room, readCredentials, publish, anonymousView]);
  const actions = useMemo(() => ({ start: () => publish(`/app/room/${room}/start`), answer: (questionId: string, optionId: string) => publish(`/app/room/${room}/answer`, { questionId, optionId }) }), [publish, room]);
  return { ...state, ...actions };
}
