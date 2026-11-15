/* Commercial Google Analytics consent only. No proprietary visitor/session tracking. */
(() => {
  'use strict';
  // Remove obsolete tracking/consent choices; do not migrate them to a new identifier.
  try {
    for (const key of ['r4r.analytics.visitor','r4r.privacy.v1','r4r.lastQuestion']) localStorage.removeItem(key);
    sessionStorage.removeItem('r4r.analytics.session');
  } catch {}
  if (window.r4rPrivacy) return;
  if (location.hostname === 'athagon.tech' || location.hostname === 'www.athagon.tech') return;
  try { if (parent !== window && parent.location.origin === location.origin && parent.r4rPrivacy) {
    window.r4rPrivacy=parent.r4rPrivacy;return;
  }} catch {}
  const gaId=document.currentScript?.dataset.gaId;
  if (gaId !== 'G-41CEFW6B5P') return; // No banner or analytics on the standalone Angular app.
  const KEY='r4r.gaConsent.v1';
  let choice=null,expires=0,loaded=false,panel;
  try { const saved=JSON.parse(localStorage.getItem(KEY));
    if(saved?.expires>Date.now() && typeof saved.allowed==='boolean'){choice=saved.allowed;expires=saved.expires;}
  } catch {}
  function apply(){
    window['ga-disable-'+gaId]=choice!==true;
    if(choice!==true){
      if(loaded)window.gtag?.('consent','update',{analytics_storage:'denied',ad_storage:'denied'});
      for(const name of document.cookie.split(';').map(c=>c.trim().split('=')[0]).filter(n=>n.startsWith('_ga')))
        for(const domain of ['', '; Domain='+location.hostname, '; Domain=.'+location.hostname.replace(/^www\./,'')])
          document.cookie=name+'=; Max-Age=0; Path=/'+domain+'; Secure; SameSite=Lax';
      return;
    }
    if(loaded){window.gtag?.('consent','update',{analytics_storage:'granted'});return;}
    loaded=true;window.dataLayer=window.dataLayer||[];
    window.gtag=function(){window.dataLayer.push(arguments);};
    window.gtag('consent','default',{analytics_storage:'granted',ad_storage:'denied',ad_user_data:'denied',ad_personalization:'denied'});
    window.gtag('js',new Date());window.gtag('config',gaId,{cookie_expires:90*86400,cookie_update:false,allow_google_signals:false,allow_ad_personalization_signals:false});
    const script=document.createElement('script');script.async=true;script.src='https://www.googletagmanager.com/gtag/js?id='+gaId;document.head.append(script);
  }
  function choose(allowed){
    choice=allowed;expires=Date.now()+180*86400000;
    try{localStorage.setItem(KEY,JSON.stringify({allowed,expires}));}catch{}
    apply();panel?.remove();panel=undefined;
  }
  function open(){
    if(panel){panel.querySelector('button').focus();return;}
    panel=document.createElement('section');panel.setAttribute('aria-label','Cookies de Google Analytics');
    panel.style.cssText='position:fixed;bottom:12px;left:12px;right:12px;max-width:640px;margin:auto;z-index:10000;padding:20px;background:white;color:#243246;border:2px solid #415166;border-radius:12px;font:16px/1.5 system-ui;max-height:85vh;overflow:auto';
    const text=document.createElement('p');text.textContent='¿Permites Google Analytics para medir la navegación de esta web? El asistente funciona si lo rechazas. El registro técnico de consultas es independiente y se explica junto al formulario.';
    const link=document.createElement('a');link.href='/politica-cookies.html';link.textContent='Política de cookies';
    const buttons=document.createElement('div');buttons.style.cssText='display:flex;gap:12px;flex-wrap:wrap;margin-top:12px';
    for(const [label,value] of [['Aceptar Google Analytics',true],['Rechazar Google Analytics',false]]){
      const button=document.createElement('button');button.type='button';button.textContent=label;
      button.style.cssText='padding:10px 16px;background:white;color:#243246;border:2px solid #415166;border-radius:6px;font:inherit;cursor:pointer';
      button.addEventListener('click',()=>choose(value));buttons.append(button);
    }
    panel.append(text,link,buttons);document.body.append(panel);
  }
  window.r4rPrivacy={open,withdraw:()=>choose(false),read:()=>({analytics:choice===true})};
  function ready(){
    const button=document.createElement('button');button.id='r4r-ga-control';button.type='button';button.textContent='Cookies de Google Analytics';
    button.style.cssText='display:block;margin:12px auto;padding:8px 12px;font:inherit';button.addEventListener('click',open);document.body.append(button);
    apply();if(!expires)open();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
