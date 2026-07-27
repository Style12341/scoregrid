import { useCallback, useEffect, useState } from "react";

type Status = "loading" | "ready" | "error";

/**
 * Load-on-mount with a retry, shared by both ranking screens.
 *
 * Tracks a cancellation flag so a response that arrives after the component
 * unmounts — or after the tournament id changed — cannot write stale rows into
 * state. Without it, switching tournaments quickly can leave the previous
 * tournament's ranking on screen under the new heading.
 */
export function useRanking<T>(load: () => Promise<T[]>) {
  const [entries, setEntries] = useState<T[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [reloadToken, setReloadToken] = useState(0);

  const retry = useCallback(() => setReloadToken((token) => token + 1), []);

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");

    load()
      .then((data) => {
        if (cancelled) return;
        setEntries(data);
        setStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [load, reloadToken]);

  return { entries, status, retry };
}
