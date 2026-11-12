# إعداد اختياري لخدمة الذكاء الاصطناعي السحابية في إميليا

إميليا تدعم الآن Cloudflare Workers AI كخيار سحابي دون مفتاح OpenAI أو بطاقة بنكية، ضمن الحصة المجانية اليومية وحدود حساب Cloudflare. الردود المحلية تبقى متاحة عند عدم إعداد الخدمة لكنها محدودة. يمكن اختيار OpenAI بدلاً من ذلك، وقد يترتب عليه دفع. رابط المشروع مضبوط على:

`https://bibicha.mebarkia041.workers.dev`

## إعداد Cloudflare

1. افتح https://dash.cloudflare.com/ ثم **Workers & Pages**.
2. افتح العامل المرتبط بالرابط أعلاه. إذا لم يكن موجوداً، أنشئ Worker بالاسم **bibicha** (وليس bibicha-ai).
3. من **Edit code** استعمل الملف `worker/src/index.js` الموجود في المستودع، ثم اضغط **Deploy**.
4. افتح **Settings → Variables and Secrets** وأضف:
   - Text variable: `ALLOWED_ORIGIN` = `https://mebarkia041-dot.github.io,https://bibicha.mebarkia041.workers.dev`
   - فعّل ربط **Workers AI** للعامل (binding name: `AI`). ملف `worker/wrangler.toml` يتضمن هذا الربط عند النشر عبر Wrangler.
   - Secret: `MOBILE_APP_TOKEN` = رمز عشوائي طويل لا يقل عن 32 حرفاً.
   - لا تحتاج `OPENAI_API_KEY` عند استخدام Workers AI. يبقى اختيارياً فقط إذا أردت استعمال OpenAI بدلاً منه.
5. لا ترسل أي مفتاح أو رمز في المحادثة، ولا تضع `OPENAI_API_KEY` في GitHub أو الهاتف.
6. في تطبيق Android أدخل رابط Worker أعلاه، ثم أدخل نفس `MOBILE_APP_TOKEN` الذي وضعته في Cloudflare. في واجهة الويب اضغط زر **ربط آمن** وأدخل الرمز نفسه؛ يُحفظ في `sessionStorage` لهذه الجلسة فقط ولا يُضمّن في ملفات الموقع.

## إعداد النشر من ملفات المشروع

أضفت ملف `worker/wrangler.toml` مضبوطاً على اسم `bibicha` حتى يتطابق مع رابط `api-config.js`. إذا كنت تستعمل Wrangler من بيئة تطوير، شغّل من جذر المستودع:

```bash
npx wrangler deploy --config worker/wrangler.toml
```

ثم أضف رمز الربط السري في Cloudflare (لا تضعه في ملف TOML):

```bash
npx wrangler secret put MOBILE_APP_TOKEN --config worker/wrangler.toml
```

## اختبار الاتصال

افتح `https://bibicha.mebarkia041.workers.dev/health`. النتيجة المتوقعة:

```json
{"service":"Emilia AI","configured":true}
```

تعني `configured: true` أن ربط Workers AI أو مفتاح OpenAI متاح، وأن `MOBILE_APP_TOKEN` موجود. يجب أيضاً اختبار رسالة فعلية من التطبيق للتأكد من استجابة النموذج. يجب أيضاً أن ينجح `/auth-check` بالرمز الصحيح، ثم إرسال رسالة فعلية من التطبيق.

## الوظائف والحدود الحالية

- الواجهة تحفظ سجل المحادثة والذاكرة محلياً على الجهاز.
- المتصفح يستعمل التعرف الصوتي والنطق إذا كانا مدعومين.
- كل طلب محادثة، من Android أو المتصفح، يحتاج `MOBILE_APP_TOKEN`؛ CORS يحدد المواقع المسموح لها في المتصفح لكنه ليس مصادقة. زر اختبار الاتصال في Android يفحص `/health` أولاً للتأكد من وجود السرّين، ثم يستعمل `/auth-check` للتحقق من الرمز فعلياً دون استدعاء OpenAI أو استهلاك طلب محادثة.
- ميزات الهاتف الحساسة تحتاج أذونات Android وتأكيد المستخدم؛ لا يتم إرسال الرسائل أو مشاركة الموقع تلقائياً.
- لا توجد حالياً مزامنة ذاكرة بين الأجهزة.
