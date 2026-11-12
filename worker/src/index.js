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
    if (!allowedOrigin || origin !== allowedOrigin) {
      return new Response("Origin not allowed", { status: 403 });
    }

    const headers = corsHeaders(origin, allowedOrigin);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers });
    }

    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") {
      return Response.json({ service: "BiBichA AI", configured: Boolean(env.OPENAI_API_KEY) }, { headers });
    }
    if (url.pathname !== "/chat" || request.method !== "POST") {
      return new Response("Not found", { status: 404, headers });
    }
    if (!env.OPENAI_API_KEY) {
      return Response.json({ error: "الخادم غير مربوط بمفتاح الذكاء الاصطناعي بعد." }, { status: 503, headers });
    }

    try {
      const body = await request.json();
      const message = String(body.message || "").trim();
      const history = Array.isArray(body.history) ? body.history.slice(-12) : [];
      const memory = Array.isArray(body.memory) ? body.memory.filter(x => typeof x === "string").slice(-30) : [];
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
        })
      });

      const data = await upstream.json();
      if (!upstream.ok) {
        return Response.json({ error: "تعذر الاتصال بخدمة الذكاء الاصطناعي حالياً." }, { status: 502, headers });
      }
      const answer = data.output_text || (data.output || [])
        .flatMap(item => item.content || [])
        .filter(item => item.type === "output_text")
        .map(item => item.text)
        .join("\n");
      if (!answer) {
        return Response.json({ error: "ما وصلنيش رد. عاود المحاولة." }, { status: 502, headers });
      }
      return Response.json({ reply: answer }, { headers });
    } catch (error) {
      return Response.json({ error: "صار خطأ في الخادم. عاود بعد لحظة." }, { status: 500, headers });
    }
  }
};
