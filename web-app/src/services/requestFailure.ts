import axios from 'axios';

export const requestFailureOf = (error: unknown): unknown =>
{
    if (axios.isAxiosError<unknown>(error)) return error.response?.data || error.message;
    return error instanceof Error ? error.message : error;
};

export const responseStatusOf = (error: unknown): number | undefined =>
    axios.isAxiosError(error) ? error.response?.status : undefined;
