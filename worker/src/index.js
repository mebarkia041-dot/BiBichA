const SYSTEM_PROMPT = `أنت بيبيشا، مساعدة ذكية تتحدث العربية والدارجة الجزائرية بأسلوب لطيف وحنون ومحترم. افهم سياق المحادثة، أجب بوضوح واختصار عند الحاجة، ولا تدّع امتلاك معلومات أو تنفيذ أفعال لم تقم بها. إذا لم تعرف شيئاً فقل ذلك بصراحة.`;

function corsHeaders(origin, allowedOrigin) {
  return {
    "Access-Control-Allow-Origin": allowedOrigin,
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Vary": "Origin"
  };
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const allowedOrigin = env.ALLOWED_ORIGIN || "";
    const url = new URL(request.url);

    // Safe public health check; direct browser navigation has no Origin header.
    if (url.pathname === "/health" && request.method === "GET") {
      const healthHeaders = origin === allowedOrigin && allowedOrigin
        ? corsHeaders(origin, allowedOrigin)
        : {};
      return Response.json(
        { service: "BiBichA AI", configured: Boolean(env.OPENAI_API_KEY) },
        { headers: healthHeaders }
      );
    }

    if (!allowedOrigin || origin !== allowedOrigin) {
      return new Response("Origin not allowed", { status: 403 });
    }

    const headers = corsHeaders(origin, allowedOrigin);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers });
    }

    if (url.pathname !== "/chat" || request.method !== "POST") {
      return new Response("Not found", { status: 404, headers });
    }
    if (!request.headers.get("Content-Type")?.toLowerCase().includes("application/json")) {
      return Response.json({ error: "صيغة الطلب غير مدعومة." }, { status: 415, headers });
    }
    if (!env.OPENAI_API_KEY) {
      return Response.json({ error: "الخادم غير مربوط بمفتاح الذكاء الاصطناعي بعد." }, { status: 503, headers });
    }

    try {
      const rawBody = await request.text();
      if (new TextEncoder().encode(rawBody).byteLength > 64000) {
        return Response.json({ error: "الطلب كبير بزاف." }, { status: 413, headers });
      }
      let body;
      try {
        body = JSON.parse(rawBody);
      } catch {
        return Response.json({ error: "محتوى الطلب غير صالح؛ أعد المحاولة." }, { status: 400, headers });
      }
      if (!body || typeof body !== "object" || Array.isArray(body) || typeof body.message !== "string") {
        return Response.json({ error: "بيانات الرسالة غير صالحة." }, { status: 400, headers });
      }
      const message = body.message.trim();
      const history = Array.isArray(body.history) ? body.history.slice(-12) : [];
      const memory = Array.isArray(body.memory)
        ? body.memory.filter(x => typeof x === "string").slice(-30).map(x => x.slice(0, 500))
        : [];
      if (!message || message.length > 4000) {
        return Response.json({ error: "الرسالة فارغة أو طويلة بزاف." }, { status: 400, headers });
      }

      const input = history
        .filter(item => item && ["user", "assistant"].includes(item.role) && typeof item.content === "string")
        .map(item => ({ role: item.role, content: item.content.slice(0, 4000) }));
      input.push({ role: "user", content: message });

      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || "gpt-4.1-mini",
          instructions: SYSTEM_PROMPT + (memory.length ? "\n\nمعلومات محفوظة بإذن المستخدم، استعملها عند صلتها بالسؤال ولا تخترع غيرها:\n- " + memory.join("\n- ") : ""),
          input,
          max_output_tokens: 700
        }),
        signal: AbortSignal.timeout(30000)
      });

      let data;
      try {
        data = await upstream.json();
      } catch {
        return Response.json({ error: "وصل رد غير مفهوم من خدمة الذكاء الاصطناعي." }, { status: 502, headers });
      }
      if (!upstream.ok) {
        return Response.json({ error: "تعذر الاتصال بخدمة الذكاء الاصطناعي حالياً." }, { status: 502, headers });
      }
      const answer = typeof data.output_text === "string" && data.output_text.trim()
        ? data.output_text.trim()
        : (Array.isArray(data.output) ? data.output : [])
          .flatMap(item => Array.isArray(item?.content) ? item.content : [])
          .filter(item => item?.type === "output_text" && typeof item.text === "string")
          .map(item => item.text)
          .join("\n").trim();
      if (!answer) {
        return Response.json({ error: "ما وصلنيش رد. عاود المحاولة." }, { status: 502, headers });
      }
      return Response.json({ reply: answer }, { headers });
    } catch (error) {
      const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError";
      return Response.json(
        { error: timedOut ? "خدمة الذكاء الاصطناعي تأخرت. عاود المحاولة." : "صار خطأ في الخادم. عاود بعد لحظة." },
        { status: timedOut ? 504 : 502, headers }
      );
    }
  }
};
