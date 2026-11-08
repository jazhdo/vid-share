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

type SQLStatement = {
    sql: string,
    params: string[]
}

export default {
	async fetch(request, env, ctx): Promise<Response> {
        const url = new URL(request.url);
        let pathname = url.pathname;

        // If someone is trying to just check what this is
        if (!pathname.startsWith("/api/")) return res(404);
        else pathname = pathname.substring(4);
        
        switch (pathname) {
            case "/db/prepare":
                // Like a single SQL preparedStatement execution
                {
                    const item = await request.json() as SQLStatement;
                    const res = await env.prod.prepare(item.sql).bind(...item.params).run();

                    console.log("Result: " + JSON.stringify(res));

                    return new Response(JSON.stringify(res.results));
                }
            case "/db/first":
                // Like a single SQL preparedStatement execution but only getting the first element
                {
                    const item = await request.json() as SQLStatement;
                    const res = await env.prod.prepare(item.sql).bind(...item.params).first();

                    console.log("First Result: " + JSON.stringify(res?.results));

                    return new Response(res === null ? null : JSON.stringify(res));
                }
            case "/db/update":
                // Like a SQL preparedStatement UPDATE / INSERT / DELETE execution
                {
                    const item = await request.json() as SQLStatement;
                    const res = await env.prod.prepare(item.sql).bind(...item.params).run();

                    console.log("Update Result: " + JSON.stringify(res));

                    return new Response(JSON.stringify(res.meta.changes));
                }
            case "/db/batch":
                // Like a mass SQL preparedStatement execution
                {
                    const payload = await request.json() as SQLStatement[];
                    const statements: D1PreparedStatement[] = [];
                    payload.forEach(payloadItem => statements.push(env.prod.prepare(payloadItem.sql).bind(...payloadItem.params)));
                    const res = await env.prod.batch(statements);

                    console.log("Batch Result: " + JSON.stringify(res));

                    return new Response(JSON.stringify(res));
                }
            case "/db/exec":
                // Like a SQL update but without any parameters
                {
                    const statement = await request.text();
                    const res = await env.prod.exec(statement);
                    
                    console.log("Exec Result: " + JSON.stringify(res));

                    return new Response(JSON.stringify(res.count));
                }
            case "/db/pfp":
                // Specialized endpoint for dealing with the bytes
                {
                    if (request.method === "POST") {
                        const payload = await request.formData();
                        const res = await env.prod.prepare("UPDATE users SET pfp = ? WHERE id = ?").bind(payload.get("pfp") as File, payload.get("id") as string).run();
                        return new Response(res.meta.changes.toString());
                    } else if (request.method === "QUERY") {
                        return new Response(await env.prod.prepare("SELECT pfp FROM users WHERE id = ?").bind(await request.text()).first<ArrayBuffer>());
                    }
                }
                break;
            case "/db/search":
                // Specialzed endpoint for dealing with pfp bytes
                {
                    const query = await request.text();
                    const res = await env.prod
                        .prepare("SELECT v.id, v.thumbnail, v.title, v.description, v.views, v.likes, v.shares, v.timestamp, u.id AS uid, u.displayname FROM videos v JOIN videos_search s ON v.rowid = s.rowid JOIN users u ON v.uid = u.id WHERE videos_search MATCH ? ORDER BY s.rank ASC")
                        .bind(query).run();
                    return new Response(JSON.stringify(res.results));
                }
        }
        return res(404);
	}
} satisfies ExportedHandler<Env>;
