import axios from 'axios'

/** El backend responde 404 cuando el token no existe (o fue reemplazado por un reenvío). */
export const axiosIsNotFound = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 404
