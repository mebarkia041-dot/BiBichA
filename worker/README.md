# ربط الذكاء الاصطناعي الحقيقي في BiBichA

هذا الدليل مخصص لإعداد خادم بيبيشا على Cloudflare. رابط المشروع مضبوط على:

`https://bibicha.mebarkia041.workers.dev`

## إعداد Cloudflare

1. افتح https://dash.cloudflare.com/ ثم **Workers & Pages**.
2. افتح العامل المرتبط بالرابط أعلاه. إذا لم يكن موجوداً، أنشئ Worker بالاسم **bibicha** (وليس bibicha-ai).
3. من **Edit code** استعمل الملف `worker/src/index.js` الموجود في المستودع، ثم اضغط **Deploy**.
4. افتح **Settings → Variables and Secrets** وأضف:
   - Text variable: `ALLOWED_ORIGIN` = `https://mebarkia041-dot.github.io,https://bibicha.mebarkia041.workers.dev`
   - Text variable: `OPENAI_MODEL` = `gpt-4.1-mini`
   - Secret: `OPENAI_API_KEY` = مفتاح OpenAI API الخاص بك.
   - Secret: `MOBILE_APP_TOKEN` = رمز عشوائي طويل لا يقل عن 32 حرفاً.
5. لا ترسل أي مفتاح أو رمز في المحادثة، ولا تضع `OPENAI_API_KEY` في GitHub أو الهاتف.
6. في تطبيق Android أدخل رابط Worker أعلاه، ثم أدخل نفس `MOBILE_APP_TOKEN` الذي وضعته في Cloudflare. في واجهة الويب اضغط زر **ربط آمن** وأدخل الرمز نفسه؛ يُحفظ في `sessionStorage` لهذه الجلسة فقط ولا يُضمّن في ملفات الموقع.

## إعداد النشر من ملفات المشروع

أضفت ملف `worker/wrangler.toml` مضبوطاً على اسم `bibicha` حتى يتطابق مع رابط `api-config.js`. إذا كنت تستعمل Wrangler من بيئة تطوير، شغّل من جذر المستودع:

```bash
npx wrangler deploy --config worker/wrangler.toml
```

ثم أضف الأسرار في Cloudflare (لا تضعها في ملف TOML):

```bash
npx wrangler secret put OPENAI_API_KEY --config worker/wrangler.toml
npx wrangler secret put MOBILE_APP_TOKEN --config worker/wrangler.toml
```

## اختبار الاتصال

افتح `https://bibicha.mebarkia041.workers.dev/health`. النتيجة المتوقعة:

```json
{"service":"BiBichA AI","configured":true}
```

هذا يعني أن Secret باسم `OPENAI_API_KEY` موجود فقط؛ لا يثبت وحده أن المفتاح صالح أو أن الفوترة مفعّلة. الاختبار النهائي هو إرسال رسالة من التطبيق.

## الوظائف والحدود الحالية

- الواجهة تحفظ سجل المحادثة والذاكرة محلياً على الجهاز.
- المتصفح يستعمل التعرف الصوتي والنطق إذا كانا مدعومين.
- كل طلب محادثة، من Android أو المتصفح، يحتاج `MOBILE_APP_TOKEN`؛ CORS يحدد المواقع المسموح لها في المتصفح لكنه ليس مصادقة.
- ميزات الهاتف الحساسة تحتاج أذونات Android وتأكيد المستخدم؛ لا يتم إرسال الرسائل أو مشاركة الموقع تلقائياً.
- لا توجد حالياً مزامنة ذاكرة بين الأجهزة.
