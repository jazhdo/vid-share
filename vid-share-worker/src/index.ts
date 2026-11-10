/**
 * Welcome to Cloudflare Workers! This is your first worker.
 *
 * - Run `npm run dev` in your terminal to start a development server
 * - Open a browser tab at http://localhost:8787/ to see your worker in action
 * - Run `npm run deploy` to publish your worker
 *
 * Bind resources to your worker in `wrangler.jsonc`. After adding bindings, a type definition for the
 * `Env` object can be regenerated with `npm run cf-typegen`.
 *
 * Learn more at https://developers.cloudflare.com/workers/
 */

// Tutorial from https://developers.cloudflare.com/workers/get-started/guide/

function res(status: number): Response {
    const res = new Response(null, {
        status: status
    });
    return res;
}

function getPath(path: string): Function | undefined {
    return path.substring(1).split("/").reduce((previousValue: unknown, currentValue: string) => {
        if (previousValue && typeof previousValue === "object") return (previousValue as Record<string, unknown>)[currentValue];
        return undefined;
    }, paths) as Function | undefined;
}

type SQLStatement = {
    sql: string,
    params: string[]
}

const paths: Record<string, unknown> = {
    "db": {
        // Like a single SQL preparedStatement execution
        "prepare": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            const item = await request.json<SQLStatement>();
            const res = await env.prod.prepare(item.sql).bind(...item.params).run();

            console.log("Result: " + JSON.stringify(res));

            return new Response(JSON.stringify(res.results));
        },
        // Like a single SQL preparedStatement execution but only getting the first element
        "first": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            const item = await request.json<SQLStatement>();
            const res = await env.prod.prepare(item.sql).bind(...item.params).first();

            console.log("First Result: " + JSON.stringify(res?.results));

            return new Response(res === null ? null : JSON.stringify(res));
        },
        // Like a SQL preparedStatement UPDATE / INSERT / DELETE execution
        "update": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            const item = await request.json<SQLStatement>();
            const res = await env.prod.prepare(item.sql).bind(...item.params).run();

            console.log("Update Result: " + JSON.stringify(res));

            return new Response(JSON.stringify(res.meta.changes));
        },
        // Like a mass SQL preparedStatement execution
        "batch": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            const result = JSON.stringify(await env.prod.batch((await request.json<SQLStatement[]>()).map(payloadItem => env.prod.prepare(payloadItem.sql).bind(...payloadItem.params))));

            console.log("Batch Result: " + result);

            return new Response(result);
        },
        // Like a SQL update but without any parameters
        "exec": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            const result = await env.prod.exec(await request.text());
            
            console.log("Exec Result: " + JSON.stringify(result));

            return new Response(JSON.stringify(result.count));
        },
        // Specialized endpoint for dealing with the bytes
        "pfp": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            if (request.method === "PUT") {
                const payload = await request.formData();
                const pfp = await (payload.get("pfp") as File).arrayBuffer();
                const id = payload.get("id") as string;
                const result = (await env.prod.prepare("UPDATE users SET pfp = ? WHERE id = ?").bind(pfp, id).run());

                return new Response(result.meta.changes.toString());
            } else if (request.method === "QUERY") {
                const result = (await env.prod.prepare("SELECT pfp FROM users WHERE id = ?").bind(await request.text()).first() as {pfp: ArrayBuffer})?.pfp || null;

                console.log("PFP Query Result: " + result);

                return new Response(new Uint8Array(result), {
                    headers: {
                        "Content-Type": "image/webp"
                    }
                });
            }
        },
        // Specialzed endpoint for dealing with pfp bytes
        "search": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
            const results = (await env.prod
                .prepare("SELECT v.id, v.thumbnail, v.title, v.description, v.views, v.likes, v.shares, v.timestamp, u.id AS uid, u.displayname, u.username FROM videos v JOIN videos_search s ON v.rowid = s.rowid JOIN users u ON v.uid = u.id WHERE videos_search MATCH ? ORDER BY s.rank ASC")
                .bind(await request.text()).run())?.results;

            console.log("Search Results: " + JSON.stringify(results));

            return new Response(results === undefined ? null : JSON.stringify(results));
        }
    },
    "vid": async (request: Request<unknown, IncomingRequestCfProperties<unknown>>, env: Env) => {
        if (request.method === "POST") {
            // Get and validate headers
            const contentType = request.headers.get("Content-Type"), fileName = request.headers.get("File-Id"), body = request.body;
            if (contentType === null || fileName === null || body === null) return res(400);

            try {
                const result = await env.vid.put(fileName, body, {
                    httpMetadata: {
                        contentType: contentType
                    }
                });
            } catch (error: any) {
                if (error instanceof Error) console.error(error.message);
                else console.error(JSON.stringify(error));
                return res(500);
            }

            return res(201);
        } else if (request.method === "GET") {
            const fileName = request.headers.get("File-Id");
            if (fileName === null) return res(400);

            const file = await env.vid.get(fileName);
            if (file === null) return res(404);

            return new Response(await file.arrayBuffer());
        } else return new Response(null, {
            status: 405,
            headers: {
                "Allow": "GET, POST"
            }
        });
    }
}

export default {
	async fetch(request, env, ctx): Promise<Response> {
        const url = new URL(request.url);
        let pathname = url.pathname;

        // If someone is trying to just check what this is
        if (!pathname.startsWith("/api/")) return res(404);
        else pathname = pathname.substring(4);
        
        const pathLogic = getPath(pathname);
        if (pathLogic === undefined) return res(404);
        
        return pathLogic(request, env);
	}
} satisfies ExportedHandler<Env>;
