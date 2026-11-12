# إعداد اختياري لخدمة الذكاء الاصطناعي السحابية في إميليا

هذا الإعداد اختياري بالكامل. إميليا تعمل بالوظائف والردود المحلية المجانية دون Cloudflare أو بطاقة أو مفتاح OpenAI. الردود المحلية محدودة وليست بديلاً كاملاً عن نموذج لغوي متقدم. إذا أردت لاحقاً خدمة سحابية، اتبع الخطوات التالية وقد تنطبق عليها تكاليف أو حدود مزود الخدمة. رابط المشروع مضبوط على:

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
{"service":"Emilia AI","configured":true}
```

تعني `configured: true` أن السرّين `OPENAI_API_KEY` و`MOBILE_APP_TOKEN` موجودان معاً؛ لا تثبت وحدها أن مفتاح OpenAI صالح أو أن الفوترة مفعّلة. يجب أيضاً أن ينجح `/auth-check` بالرمز الصحيح، ثم إرسال رسالة فعلية من التطبيق.

## الوظائف والحدود الحالية

- الواجهة تحفظ سجل المحادثة والذاكرة محلياً على الجهاز.
- المتصفح يستعمل التعرف الصوتي والنطق إذا كانا مدعومين.
- كل طلب محادثة، من Android أو المتصفح، يحتاج `MOBILE_APP_TOKEN`؛ CORS يحدد المواقع المسموح لها في المتصفح لكنه ليس مصادقة. زر اختبار الاتصال في Android يفحص `/health` أولاً للتأكد من وجود السرّين، ثم يستعمل `/auth-check` للتحقق من الرمز فعلياً دون استدعاء OpenAI أو استهلاك طلب محادثة.
- ميزات الهاتف الحساسة تحتاج أذونات Android وتأكيد المستخدم؛ لا يتم إرسال الرسائل أو مشاركة الموقع تلقائياً.
- لا توجد حالياً مزامنة ذاكرة بين الأجهزة.
