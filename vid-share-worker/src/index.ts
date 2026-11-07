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

export default {
	async fetch(request, env, ctx): Promise<Response> {
        const url = new URL(request.url);
        let pathname = url.pathname;

        // If someone is trying to just check what this is
        if (!pathname.startsWith("/api/")) return res(404);
        else pathname = pathname.substring(4);
        console.log("Request towards api path " + pathname + " recieved");
        
        // Payload
        const payload = await request.json() as {sql: string, params: string[]}[];

        switch (pathname) {
            case "/db/prepare":
                // Like a single SQL preparedStatement execution
                {
                    const item = payload[0];
                    const res = await env.prod.prepare(item.sql).bind(item.params).run();

                    console.log("Results: " + JSON.stringify(res));
                    return new Response(JSON.stringify(res.results));
                }
                break;
            case "/db/batch":
                // Like a mass SQL preparedStatement execution
                {
                    const statements: D1PreparedStatement[] = [];
                    payload.forEach(payloadItem => {
                        statements.push(env.prod.prepare(payloadItem.sql).bind(payloadItem.params));
                    });
                    const res = await env.prod.batch(statements);

                    console.log("Batch Result: " + JSON.stringify(res));

                    return new Response(JSON.stringify(res));
                }
                break;
            case "/db/update":
                // Like a SQL update
                {
                    const statement = payload[0];
                    const res = await env.prod.exec(statement.sql);
                    
                    console.log("Exec Result: " + JSON.stringify(res));

                    return new Response(JSON.stringify(res.count));
                }
                break;
            default:
                return res(404);
        }

		return new Response("Hello Worker!");
	}
} satisfies ExportedHandler<Env>;
