import axios from "axios";
import { toApiError } from "@/lib/api";

const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: "Revisá los datos ingresados.",
  NOT_FOUND: "No encontramos el recurso solicitado.",
  FORBIDDEN: "No tenés permisos para realizar esta acción.",
  TOURNAMENT_NOT_ACTIVE: "El torneo no está disponible para esta acción.",
  INVALID_MATCH_STATE: "El partido no permite esa modificación en su estado actual.",
  NOT_ENROLLED: "No estás inscripto en este torneo.",
  PREDICTION_LOCKED: "El partido ya comenzó y los pronósticos están cerrados.",
  DOWNSTREAM_UNAVAILABLE: "El servicio no está disponible. Intentá nuevamente.",
  NOT_REGISTERED: "El equipo no está registrado en este torneo.",
  NOT_IN_GROUP: "Los equipos deben pertenecer al grupo seleccionado.",
  ALREADY_IN_GROUP: "Uno de los equipos ya pertenece a otro grupo.",
};

export function apiErrorMessage(error: unknown, fallback: string): string {
  const apiError = toApiError(error);
  return (apiError && ERROR_MESSAGES[apiError.error]) || fallback;
}

export function isNotFoundError(error: unknown): boolean {
  return toApiError(error)?.status === 404;
}

/**
 * The request never reached a working service: no response at all (network
 * down, gateway unreachable) or 503 DOWNSTREAM_UNAVAILABLE (an open breaker).
 * Sending more requests right away would only fail the same way.
 */
export function isServiceUnavailable(error: unknown): boolean {
  return axios.isAxiosError(error) && (!error.response || error.response.status === 503);
}

/** apiErrorMessage, with a message for a request that got no response at all. */
export function requestErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error) && !error.response) {
    return "No hay conexión con el servidor. Revisá la red y volvé a intentarlo.";
  }
  return apiErrorMessage(error, fallback);
}
