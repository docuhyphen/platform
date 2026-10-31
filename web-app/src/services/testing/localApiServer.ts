import {createServer, IncomingMessage, ServerResponse} from "node:http";
import {AddressInfo} from "node:net";

export interface LocalAnswer {
    status: number;
    contentType?: string;
    body: string;
}

export type LocalAnswerer = (method: string, path: string) => LocalAnswer | "drop";

export interface ReceivedRequest {
    method: string;
    path: string;
    body: string;
}

export interface LocalApiServer {
    origin: string;
    served: string[];
    received: ReceivedRequest[];
    close: () => Promise<void>;
}

export const FILE_ROUTE_REFUSAL_TYPE = "*/*";

export const jsonAnswer = (status: number, body: unknown, contentType = "application/json"): LocalAnswer =>
    ({status, contentType, body: JSON.stringify(body)});

export const fileAnswer = (content: string, contentType = "application/pdf"): LocalAnswer =>
    ({status: 200, contentType, body: content});

const allowCrossOrigin = (request: IncomingMessage, response: ServerResponse) => {
    response.setHeader("Access-Control-Allow-Origin", request.headers.origin ?? "*");
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE");
    response.setHeader("Access-Control-Allow-Headers", request.headers["access-control-request-headers"] ?? "*");
};

export const startLocalApiServer = async (answer: LocalAnswerer): Promise<LocalApiServer> => {
    const served: string[] = [];
    const received: ReceivedRequest[] = [];
    const server = createServer((request, response) => {
        allowCrossOrigin(request, response);
        if (request.method === "OPTIONS") {
            response.writeHead(204);
            response.end();
            return;
        }
        const method = request.method ?? "GET";
        const path = request.url ?? "/";
        served.push(`${method} ${path}`);
        const parts: Buffer[] = [];
        request.on("data", (part: Buffer) => parts.push(part));
        request.on("end", () => {
            received.push({method, path, body: Buffer.concat(parts).toString("utf8")});
            const answered = answer(method, path);
            if (answered === "drop") {
                request.socket.destroy();
                return;
            }
            response.writeHead(answered.status, answered.contentType ? {"Content-Type": answered.contentType} : {});
            response.end(answered.body);
        });
    });
    await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
    return {
        origin: `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
        served,
        received,
        close: () => new Promise<void>((resolve, reject) => {
            server.close(error => error ? reject(error) : resolve());
            server.closeAllConnections();
        }),
    };
};

export const textOf = (content: Blob): Promise<string> =>
    new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(content);
    });
