import axios from "axios";

const DECODABLE_ERROR_BODY_BYTES = 64 * 1024;

const textOf = (body: Blob): Promise<string> =>
    new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(body);
    });

const statedObject = (text: string): Record<string, unknown> | null => {
    try {
        const parsed: unknown = JSON.parse(text);
        return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
            ? parsed as Record<string, unknown>
            : null;
    } catch {
        return null;
    }
};

export const decodeJsonErrorBody = async (error: unknown): Promise<void> => {
    if (!axios.isAxiosError<unknown>(error) || !error.response) return;
    const body = error.response.data;
    if (!(body instanceof Blob) || body.size > DECODABLE_ERROR_BODY_BYTES) return;
    const stated = statedObject(await textOf(body).catch(() => ""));
    if (stated) error.response.data = stated;
};
