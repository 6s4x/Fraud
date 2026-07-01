import React, { useRef, useEffect, useState } from 'react';

export default function Console({ lines, onCommand, connected }) {
  const [input, setInput] = useState('');
  const bottomRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines]);

  const handleSend = () => {
    const cmd = input.trim();
    if (!cmd) return;
    onCommand(cmd);
    setInput('');
    inputRef.current?.focus();
  };

  const handleKey = (e) => {
    if (e.key === 'Enter') handleSend();
  };

  return (
    <div className="console-wrapper">
      <div className="console" onClick={() => inputRef.current?.focus()}>
        {lines.length === 0 && (
          <div style={{ color: 'var(--text-muted)', fontSize: '12px' }}>Waiting for console output...</div>
        )}
        {lines.map((l, i) => {
          let cls = 'console-line';
          const text = l.line || '';
          if (text.includes('ERROR') || text.includes('Exception')) cls += ' error';
          else if (text.includes('WARN')) cls += ' warn';
          return <div key={i} className={cls}>{text}</div>;
        })}
        <div ref={bottomRef} />
      </div>
      <div className="console-input-area">
        <input
          ref={inputRef}
          className="console-input"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKey}
          placeholder={connected ? 'Enter command...' : 'Disconnected...'}
          disabled={!connected}
        />
        <button className="console-send" onClick={handleSend} disabled={!connected}>
          Send
        </button>
      </div>
    </div>
  );
}
