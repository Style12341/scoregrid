import { useState } from "react";
import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

/**
 * Ask before an action that cannot be undone.
 *
 * The description must say the consequence ("Los pronósticos se cierran y no
 * se puede deshacer"), not just repeat the question. The confirm label repeats
 * the action's verb, so the button the user presses says what will happen.
 *
 * `onConfirm` may return a promise: the dialog shows a pending state while it
 * runs, closes when it resolves, and stays open when it rejects so the caller
 * can report the error (usually with a toast) and the user can retry.
 *
 * Use it either with a `trigger` element, or controlled with `open` and
 * `onOpenChange` when the action starts elsewhere (a form submit, say).
 */
export function ConfirmDialog({
  trigger,
  open: controlledOpen,
  onOpenChange,
  title,
  description,
  confirmLabel,
  pendingLabel,
  dismissLabel = "Volver",
  destructive = false,
  onConfirm,
}: {
  trigger?: ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  /** Shown on the confirm button while onConfirm runs. Defaults to confirmLabel. */
  pendingLabel?: string;
  /**
   * The label of the button that closes without acting. "Volver" by default,
   * not "Cancelar", because "Cancelar torneo" is itself an action here.
   */
  dismissLabel?: string;
  destructive?: boolean;
  onConfirm: () => void | Promise<void>;
}) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const open = controlledOpen ?? uncontrolledOpen;

  function setOpen(next: boolean) {
    // Closing mid-request would hide the outcome; wait for it.
    if (pending && !next) return;
    setUncontrolledOpen(next);
    onOpenChange?.(next);
  }

  async function handleConfirm() {
    setPending(true);
    try {
      await onConfirm();
      setPending(false);
      setUncontrolledOpen(false);
      onOpenChange?.(false);
    } catch {
      // The caller reports the failure; keep the dialog open to allow a retry.
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      {trigger && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent showCloseButton={!pending}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription asChild>
            <div className="flex flex-col gap-2 text-sm text-muted-foreground">
              {description}
            </div>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="outline" disabled={pending}>
              {dismissLabel}
            </Button>
          </DialogClose>
          <Button
            variant={destructive ? "destructive" : "default"}
            onClick={handleConfirm}
            disabled={pending}
            aria-busy={pending || undefined}
          >
            {pending && <Loader2 className="animate-spin" aria-hidden="true" />}
            {pending ? (pendingLabel ?? confirmLabel) : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
