/* First-party consent preferences. No identifiers or external analytics before opt-in. */
(() => {
  'use strict';
  if (window.r4rPrivacy) return;
  try { if (parent !== window && parent.location.origin === location.origin && parent.r4rPrivacy) {
    window.r4rPrivacy = parent.r4rPrivacy; return;
  } } catch { /* Standalone cross-origin frames keep their own domain preferences. */ }
  const VERSION = '2026-11-v1', KEY = 'r4r.privacy.v1', VISITOR = 'r4r.analytics.visitor', SESSION = 'r4r.analytics.session';
  const days = n => n * 86400000;
  const spanish = location.hostname.includes('riansares') || document.documentElement.lang.startsWith('es');
  const policy = location.hostname.includes('riansares') ? '/politica-privacidad.html' : '/privacy.html';
  let state = {version:VERSION, analytics:false, history:false, expires:0};
  try { const saved = JSON.parse(localStorage.getItem(KEY)); if (saved?.version === VERSION && saved.expires > Date.now()) state = saved; } catch {}
  const clearIds = () => { try { localStorage.removeItem(VISITOR); sessionStorage.removeItem(SESSION); } catch {} };
  if (!state.analytics) clearIds();
  function read() {
    if (state.expires && state.expires <= Date.now()) { state = {version:VERSION,analytics:false,history:false,expires:0}; clearIds(); }
    return {...state};
  }
  function headers() {
    const current = read(), result = {};
    if (current.history) result['X-R4R-History-Consent'] = VERSION;
    if (!current.analytics) return result;
    try {
      let visitor = JSON.parse(localStorage.getItem(VISITOR));
      if (!visitor?.id || visitor.expires <= Date.now()) {
        visitor = {id:crypto.randomUUID(), expires:Date.now() + days(90)};
        localStorage.setItem(VISITOR, JSON.stringify(visitor));
      }
      let session = sessionStorage.getItem(SESSION);
      if (!session) { session = crypto.randomUUID(); sessionStorage.setItem(SESSION,session); }
      result['X-R4R-Analytics-Consent'] = VERSION;
      result['X-R4R-Visitor'] = visitor.id;
      result['X-R4R-Session'] = session;
    } catch { /* A blocked store means no persistent tracking. */ }
    return result;
  }
  const gaId = document.currentScript?.dataset.gaId;
  let gaLoaded = false;
  function analytics() {
    if (!gaId) return;
    window['ga-disable-' + gaId] = !read().analytics;
    if (!read().analytics) {
      if (gaLoaded) window.gtag?.('consent', 'update', {analytics_storage:'denied', ad_storage:'denied'});
      for (const name of document.cookie.split(';').map(c=>c.trim().split('=')[0]).filter(n=>n.startsWith('_ga'))) {
        for (const domain of ['', '; Domain=' + location.hostname, '; Domain=.' + location.hostname.replace(/^www\./,'')])
          document.cookie = name + '=; Max-Age=0; Path=/' + domain + '; Secure; SameSite=Lax';
      }
      return;
    }
    if (gaLoaded) { window.gtag?.('consent','update',{analytics_storage:'granted',ad_storage:'denied'}); return; }
    gaLoaded = true;
    window.dataLayer = window.dataLayer || [];
    window.gtag = function() { window.dataLayer.push(arguments); };
    window.gtag('consent','default',{analytics_storage:'granted',ad_storage:'denied',ad_user_data:'denied',ad_personalization:'denied'});
    window.gtag('js',new Date()); window.gtag('config',gaId,{cookie_expires:90*86400,cookie_update:false,allow_google_signals:false,allow_ad_personalization_signals:false});
    const script = document.createElement('script'); script.async = true;
    script.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(gaId);
    document.head.append(script);
  }
  let panel;
  const queryReferences = new Set();
  function choose(analyticsAllowed, historyAllowed) {
    const old=read(), withdrawingAnalytics=old.analytics&&!analyticsAllowed, withdrawingHistory=old.history&&!historyAllowed;
    if (withdrawingAnalytics || withdrawingHistory) {
      let visitor; try { visitor=JSON.parse(localStorage.getItem(VISITOR))?.id; } catch {}
      // Rights operation, not analytics. Never creates an identifier or transmits question text.
      fetch('https://r4r-api.athagon.tech/api/rag/usage/withdraw', {method:'POST',
        headers:{'Content-Type':'application/json',...(visitor?{'X-R4R-Visitor':visitor}:{})},
        body:JSON.stringify({references:[...queryReferences],analytics:withdrawingAnalytics,history:withdrawingHistory}),keepalive:true}).catch(()=>{});
    }
    state = {version:VERSION,analytics:analyticsAllowed,history:historyAllowed,expires:Date.now()+days(180)};
    try { localStorage.setItem(KEY,JSON.stringify(state)); } catch {}
    if (!analyticsAllowed) clearIds();
    analytics(); panel?.remove(); panel = undefined;
    window.dispatchEvent(new Event('r4r:privacy-change'));
  }
  function open() {
    if (panel) { panel.querySelector('button').focus(); return; }
    panel = document.createElement('section'); panel.setAttribute('aria-label',spanish?'Preferencias de privacidad':'Privacy preferences');
    panel.style.cssText = 'position:fixed;bottom:12px;left:12px;right:12px;max-width:680px;margin:auto;z-index:10000;padding:20px;background:#fff;color:#243246;border:2px solid #415166;border-radius:12px;box-shadow:0 6px 30px #0004;font:16px/1.5 system-ui;max-height:85vh;overflow:auto';
    const intro = document.createElement('p');
    intro.textContent = spanish ? 'El asistente funciona sin aceptar estas opciones. Elige por separado si permites analítica de visitas recurrentes y conservar preguntas y respuestas para evaluación y mejora. No uses el asistente para datos sensibles.' : 'The assistant works without accepting these options. Choose separately whether to allow returning-visit analytics and saving questions and answers for evaluation and improvement. Do not submit sensitive data.';
    const link = document.createElement('a'); link.href = policy; link.target = '_blank'; link.rel = 'noopener'; link.textContent = spanish?'Política de privacidad':'Privacy policy';
    panel.append(intro,link);
    const checks = [];
    for (const [name,text] of [['analytics',spanish?'Analítica: identificador aleatorio de navegador (90 días), separado por dominio.':'Analytics: random browser identifier (90 days), separate for each domain.'],['history',spanish?'Conservar el texto completo de mis preguntas, respuestas y auditorías para evaluar y mejorar el asistente.':'Save the full text of my questions, answers and audits to evaluate and improve the assistant.']]) {
      const label=document.createElement('label'); label.style.display='block'; label.style.margin='12px 0';
      const input=document.createElement('input');input.type='checkbox'; input.checked=read()[name]; input.dataset.purpose=name;
      label.append(input,document.createTextNode(' '+text)); panel.append(label);checks.push(input);
    }
    const buttons=document.createElement('div');buttons.style.cssText='display:flex;flex-wrap:wrap;gap:12px';
    for (const [text,action] of [[spanish?'Aceptar ambas':'Accept both',()=>choose(true,true)], [spanish?'Rechazar ambas':'Reject both',()=>choose(false,false)], [spanish?'Guardar selección':'Save selection',()=>choose(checks[0].checked,checks[1].checked)]]) {
      const button=document.getElementById('r4r-privacy-control') || document.createElement('button');button.id='r4r-privacy-control';button.type='button';button.textContent=text;
      button.style.cssText='padding:10px 16px;background:#fff;color:#243246;border:2px solid #415166;border-radius:6px;font:inherit;cursor:pointer';
      button.addEventListener('click',action);buttons.append(button);
    }
    panel.append(buttons);document.body.append(panel);
  }
  window.r4rPrivacy = {read, headers, open, withdraw:()=>choose(false,false), noteQuery:id=>{ if(queryReferences.size>=100) queryReferences.delete(queryReferences.values().next().value); queryReferences.add(id); }};
  window.addEventListener('storage', event => { if (event.key === KEY) {
    try { const saved=JSON.parse(event.newValue); state=saved?.version===VERSION?saved:{version:VERSION,analytics:false,history:false,expires:0}; } catch { state={version:VERSION,analytics:false,history:false,expires:0}; }
    if (!read().analytics) clearIds(); analytics(); window.dispatchEvent(new Event('r4r:privacy-change'));
  }});
  function ready() {
    const button=document.getElementById('r4r-privacy-control') || document.createElement('button');button.id='r4r-privacy-control';button.type='button';button.textContent=spanish?'Privacidad y consentimiento':'Privacy & consent';
    button.style.cssText='display:block;margin:12px auto;padding:8px 12px;font:inherit';button.addEventListener('click',open);document.body.append(button);
    analytics();
    // Never bake a banner into prerendered HTML.
    if (!new URLSearchParams(location.search).has('prerender') && !read().expires) open();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',ready,{once:true}); else ready();
})();
