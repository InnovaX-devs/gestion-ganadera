import { Button } from './Button'
import { Dialog } from './Dialog'
import { FormError } from './FormError'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  loading?: boolean
  error?: string | null
  onConfirm: () => void
  onClose: () => void
}

/** Confirmación para acciones destructivas (en lugar de window.confirm). */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  loading,
  error,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onClose={onClose} title={title} description={description} size="sm">
      <div className="space-y-4">
        <FormError message={error ?? null} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button variant="danger" onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
