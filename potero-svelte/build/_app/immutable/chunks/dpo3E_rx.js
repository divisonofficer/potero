import{i as _e,b as Le,_ as le}from"./CBfW9mKF.js";import{f as V,a as D,c as Ne}from"./CDd--OGd.js";import{o as qe,a as Ye}from"./DTVzISQa.js";import{a as Je,f as Ze,H as Ge,h as oe,g as Xe,C as Qe,i as Pe,j as Ce,k as Ee,e as $e,l as et,n as _,o as F,u as tt,q as w,t as x,v as Se,w as r,x as rt,y as u,z as m,A as at,B as nt}from"./Cxiy8xzw.js";import{e as Te,s as se,d as it}from"./C-_Un3-0.js";import{b as lt,s as Me,r as ot,u as st}from"./h7PB39s8.js";function dt(O,i){let v=null,d=oe;var c;if(oe){v=$e;for(var g=Xe(document.head);g!==null&&(g.nodeType!==Qe||g.data!==O);)g=Pe(g);if(g===null)Ce(!1);else{var p=Pe(g);g.remove(),Ee(p)}}oe||(c=document.head.appendChild(Je()));try{Ze(()=>i(c),Ge)}finally{d&&(Ce(!0),Ee(v))}}var ct=V(`<style>/* PDF.js text layer styles */
		.textLayer {
			position: absolute;
			text-align: initial;
			inset: 0;
			overflow: hidden;
			opacity: 0.25;
			line-height: 1;
			-webkit-text-size-adjust: none;
			-moz-text-size-adjust: none;
			text-size-adjust: none;
			forced-color-adjust: none;
			transform-origin: 0 0;
			z-index: 2;
		}

		.textLayer :is(span, br) {
			color: transparent;
			position: absolute;
			white-space: pre;
			cursor: text;
			transform-origin: 0% 0%;
		}

		.textLayer span.markedContent {
			top: 0;
			height: 0;
		}

		.textLayer .highlight {
			margin: -1px;
			padding: 1px;
			background-color: rgb(180 0 170 / 40%);
			border-radius: 4px;
		}

		.textLayer .highlight.appended {
			position: initial;
		}

		.textLayer .highlight.begin {
			border-radius: 4px 0 0 4px;
		}

		.textLayer .highlight.end {
			border-radius: 0 4px 4px 0;
		}

		.textLayer .highlight.middle {
			border-radius: 0;
		}

		.textLayer .highlight.selected {
			background-color: rgb(0 100 0 / 40%);
		}

		.textLayer ::selection {
			background: rgb(0 0 255 / 30%);
		}

		.textLayer br::selection {
			background: transparent;
		}

		.textLayer .endOfContent {
			display: block;
			position: absolute;
			inset: 100% 0 0;
			z-index: -1;
			cursor: default;
			-webkit-user-select: none;
			-moz-user-select: none;
			user-select: none;
		}

		.textLayer .endOfContent.active {
			top: 0;
		}</style>`),ut=V('<div class="flex h-full items-center justify-center"><div class="flex flex-col items-center gap-3"><div class="h-10 w-10 animate-spin rounded-full border-3 border-blue-500 border-t-transparent"></div> <p class="text-sm text-neutral-500 dark:text-neutral-400">Loading PDF...</p></div></div>'),gt=V('<div class="flex h-full flex-col items-center justify-center text-neutral-500 dark:text-neutral-400"><svg class="mb-4 h-16 w-16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg> <p class="text-lg font-medium">Failed to load PDF</p> <p class="mt-1 text-sm"> </p> <button class="mt-4 rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700">Retry</button></div>'),ht=V('<div class="flex h-full flex-col bg-neutral-100 dark:bg-neutral-900"><div class="flex items-center gap-1 border-b bg-white px-2 py-1.5 shadow-sm dark:bg-neutral-800"><div class="flex items-center gap-1"><button class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 disabled:opacity-40 dark:text-neutral-300 dark:hover:bg-neutral-700" title="Previous page"><svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"></path></svg></button> <div class="flex items-center gap-1 text-sm"><input type="text" class="w-12 rounded border border-neutral-300 bg-white px-2 py-1 text-center text-sm focus:border-blue-500 focus:outline-none dark:border-neutral-600 dark:bg-neutral-700"/> <span class="text-neutral-500 dark:text-neutral-400"> </span></div> <button class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 disabled:opacity-40 dark:text-neutral-300 dark:hover:bg-neutral-700" title="Next page"><svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"></path></svg></button></div> <div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div> <div class="flex items-center gap-1 rounded-md border border-neutral-300 p-0.5 dark:border-neutral-600"><button title="Single page view"><svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="3" width="14" height="18" rx="2"></rect></svg></button> <button title="Continuous scroll view"><svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="2" width="14" height="8" rx="1"></rect><rect x="5" y="14" width="14" height="8" rx="1"></rect></svg></button></div> <div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div> <div class="flex items-center gap-1"><button class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700" title="Zoom out (Ctrl+-)"><svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><path d="M21 21l-4.35-4.35M8 11h6"></path></svg></button> <span class="min-w-14 text-center text-sm text-neutral-600 dark:text-neutral-300"> </span> <button class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700" title="Zoom in (Ctrl++)"><svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><path d="M21 21l-4.35-4.35M11 8v6M8 11h6"></path></svg></button></div> <div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div> <div class="flex items-center gap-1"><button class="rounded px-2 py-1 text-sm text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700" title="Fit to width">Fit Width</button> <button class="rounded px-2 py-1 text-sm text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700" title="Fit to page">Fit Page</button></div> <div class="ml-auto flex items-center gap-1"><button class="rounded bg-blue-100 px-3 py-1 text-sm font-medium text-blue-700 dark:bg-blue-900 dark:text-blue-300" title="Reader view (current)">Reader</button> <button class="rounded px-3 py-1 text-sm text-neutral-500 hover:bg-neutral-100 dark:text-neutral-400 dark:hover:bg-neutral-700" title="Blog view (coming soon)" disabled>Blog</button></div></div> <div class="flex-1 overflow-auto p-6" style="scroll-behavior: smooth;"><!></div></div>');function bt(O,i){et(i,!0);let v,d,c=_(F(i.initialState?.currentPage??1)),g=_(0),p=_(F(i.initialState?.scale??1.2)),z=_(!0),I=_(null),L=_(F(String(i.initialState?.currentPage??1))),b=_(F(i.initialState?.viewMode??"scroll")),U=_(F(i.initialState?{top:i.initialState.scrollTop,left:i.initialState.scrollLeft}:null)),h=null,E=new Map,P=null,y=new Set;async function De(){P||(P=await le(()=>import("./OfKI3oAY.js"),[],import.meta.url),P.GlobalWorkerOptions.workerSrc=new URL(""+new URL("../assets/pdf.worker.min.LyOxJPrg.mjs",import.meta.url).href,import.meta.url).toString())}async function B(){if(i.pdfUrl){u(z,!0),u(I,null),y.clear();try{if(await De(),!P)throw new Error("PDF.js not initialized");let e=i.pdfUrl;if(i.pdfUrl.startsWith("/")||i.pdfUrl.startsWith("~")){const t=await fetch(`/api/pdf/file?path=${encodeURIComponent(i.pdfUrl)}`);if(!t.ok)throw new Error("Failed to load PDF file");const a=await t.blob();e=URL.createObjectURL(a)}h=await P.getDocument(e).promise,u(g,h.numPages,!0),i.initialState?.currentPage&&i.initialState.currentPage<=r(g)?(u(c,i.initialState.currentPage,!0),u(L,String(r(c)),!0)):(u(c,1),u(L,"1")),await new Promise(t=>setTimeout(t,0)),r(b)==="scroll"?await de():await K(r(c))}catch(e){console.error("Failed to load PDF:",e),u(I,e instanceof Error?e.message:"Failed to load PDF",!0)}finally{u(z,!1)}}}let A=0,H=0;async function de(){if(!h||!d)return;d.innerHTML="",y.clear();const n=(await h.getPage(1)).getViewport({scale:r(p)});A=n.width,H=n.height;const t=document.createElement("div");t.className="pages-wrapper",t.style.minWidth=`${A}px`,t.style.width="100%",t.style.display="flex",t.style.flexDirection="column",t.style.alignItems="center";for(let s=1;s<=r(g);s++){const o=document.createElement("div");o.className="page-wrapper relative mb-4 flex justify-center",o.dataset.page=String(s),o.style.minHeight=`${H}px`,o.style.width="100%";const l=document.createElement("div");l.className="page-placeholder bg-white shadow-lg flex items-center justify-center",l.style.width=`${A}px`,l.style.height=`${H}px`,l.innerHTML=`
				<div class="flex flex-col items-center text-neutral-400">
					<div class="h-8 w-8 animate-spin rounded-full border-2 border-neutral-300 border-t-blue-500"></div>
					<span class="mt-2 text-sm">Page ${s}</span>
				</div>
			`,o.appendChild(l),t.appendChild(o)}d.appendChild(t),r(U)&&(d.scrollTop=r(U).top,d.scrollLeft=r(U).left,u(U,null)),Fe();const a=Math.min(3,r(g));for(let s=1;s<=a;s++)await ce(s)}function Fe(){if(!d)return;const e=new IntersectionObserver(n=>{n.forEach(t=>{if(t.isIntersecting){const a=parseInt(t.target.dataset.page||"0");a>0&&!y.has(a)&&ce(a)}})},{root:d,rootMargin:"200px 0px",threshold:.01});d.querySelectorAll(".page-wrapper").forEach(n=>{e.observe(n)})}async function ce(e){if(!h||!d||y.has(e))return;const n=d.querySelector(`[data-page="${e}"]`);if(n){y.add(e);try{const t=await h.getPage(e),a=t.getViewport({scale:r(p)}),s=window.devicePixelRatio||1,o=document.createElement("div");o.className="page-container relative",o.style.width=`${a.width}px`,o.style.height=`${a.height}px`;const l=document.createElement("canvas");l.width=Math.floor(a.width*s),l.height=Math.floor(a.height*s),l.style.width=`${a.width}px`,l.style.height=`${a.height}px`,l.className="shadow-lg bg-white block",o.appendChild(l);const f=document.createElement("div");f.className="textLayer absolute top-0 left-0",f.style.width=`${a.width}px`,f.style.height=`${a.height}px`,o.appendChild(f),n.innerHTML="",n.appendChild(o);const k=l.getContext("2d");if(k){k.scale(s,s);const C=t.render({canvasContext:k,viewport:a,canvas:l});E.set(e,C),await C.promise,E.delete(e)}const ne=await t.getTextContent();if(P){const{TextLayer:C}=await le(async()=>{const{TextLayer:ie}=await import("./OfKI3oAY.js");return{TextLayer:ie}},[],import.meta.url);await new C({textContentSource:ne,container:f,viewport:a}).render()}}catch(t){if(t instanceof Error&&t.message.includes("cancelled"))return;console.error(`Failed to render page ${e}:`,t),y.delete(e)}}}async function K(e){if(!h||!d)return;E.forEach(t=>t.cancel()),E.clear(),d.innerHTML="",y.clear();const n=document.createElement("div");n.className="page-wrapper relative flex justify-center",n.dataset.page=String(e),d.appendChild(n),y.add(e);try{const t=await h.getPage(e),a=t.getViewport({scale:r(p)}),s=window.devicePixelRatio||1,o=document.createElement("div");o.className="page-container relative",o.style.width=`${a.width}px`,o.style.height=`${a.height}px`;const l=document.createElement("canvas");l.width=Math.floor(a.width*s),l.height=Math.floor(a.height*s),l.style.width=`${a.width}px`,l.style.height=`${a.height}px`,l.className="shadow-lg bg-white block",o.appendChild(l);const f=document.createElement("div");f.className="textLayer absolute top-0 left-0",f.style.width=`${a.width}px`,f.style.height=`${a.height}px`,o.appendChild(f),n.appendChild(o);const k=l.getContext("2d");k&&(k.scale(s,s),await t.render({canvasContext:k,viewport:a,canvas:l}).promise);const ne=await t.getTextContent();if(P){const{TextLayer:C}=await le(async()=>{const{TextLayer:ie}=await import("./OfKI3oAY.js");return{TextLayer:ie}},[],import.meta.url);await new C({textContentSource:ne,container:f,viewport:a}).render()}i.onPageChange?.(r(c),r(g))}catch(t){if(t instanceof Error&&t.message.includes("cancelled"))return;console.error("Failed to render page:",t)}}function N(e){e<1||e>r(g)||(u(c,e,!0),u(L,String(e),!0),r(b)==="single"?K(r(c)):d?.querySelector(`[data-page="${e}"]`)?.scrollIntoView({behavior:"smooth",block:"start"}))}function ue(e){const n=e.target,t=parseInt(n.value);!isNaN(t)&&t>=1&&t<=r(g)?N(t):u(L,String(r(c)),!0)}function ge(){N(r(c)+1)}function he(){N(r(c)-1)}function q(){u(p,Math.min(r(p)+.25,4),!0),S()}function Y(){u(p,Math.max(r(p)-.25,.5),!0),S()}async function S(){y.clear(),r(b)==="scroll"?await de():await K(r(c))}function Ie(){!h||!v||h.getPage(r(c)).then(e=>{const n=e.getViewport({scale:1});u(p,(v.clientWidth-100)/n.width),S()})}function Ue(){!h||!v||h.getPage(r(c)).then(e=>{const n=e.getViewport({scale:1}),t=(v.clientWidth-100)/n.width,a=(v.clientHeight-150)/n.height;u(p,Math.min(t,a),!0),S()})}async function pe(e){r(b)!==e&&(u(b,e,!0),await S())}let J=null,Z=!1;function Re(){if(!d||r(b)!=="scroll"||Z)return;const e=d.querySelectorAll(".page-wrapper");if(e.length===0)return;const n=d.getBoundingClientRect();let t=r(c),a=1/0;e.forEach(s=>{const o=s.getBoundingClientRect();if(o.height===0)return;const l=Math.abs(o.top-n.top);o.top<=n.top+n.height/2&&l<a&&(a=l,t=parseInt(s.dataset.page||"1"))}),t!==r(c)&&t>=1&&t<=r(g)&&(Z=!0,u(c,t,!0),u(L,String(t),!0),i.onPageChange?.(r(c),r(g)),requestAnimationFrame(()=>{Z=!1})),i.tabId&&(J&&clearTimeout(J),J=setTimeout(()=>{R()},300))}function fe(e){e.target instanceof HTMLInputElement||(r(b)==="single"&&(e.key==="ArrowRight"||e.key==="PageDown"?(e.preventDefault(),ge()):(e.key==="ArrowLeft"||e.key==="PageUp")&&(e.preventDefault(),he())),(e.ctrlKey||e.metaKey)&&e.key==="="?(e.preventDefault(),q()):(e.ctrlKey||e.metaKey)&&e.key==="-"&&(e.preventDefault(),Y()))}function xe(e){(e.ctrlKey||e.metaKey)&&(e.preventDefault(),e.stopPropagation(),e.deltaY<0?q():e.deltaY>0&&Y())}function We(){const e=window.getSelection();e&&e.toString().trim()&&i.onTextSelect?.(e.toString().trim())}function R(){!i.tabId||!d||st(i.tabId,{scrollTop:d.scrollTop,scrollLeft:d.scrollLeft,currentPage:r(c),scale:r(p),viewMode:r(b)})}let W="";qe(()=>{window.addEventListener("keydown",fe),v?.addEventListener("wheel",xe,{passive:!1}),window.addEventListener("beforeunload",R),i.pdfUrl&&i.pdfUrl!==W&&(W=i.pdfUrl,B())}),Ye(()=>{R(),window.removeEventListener("keydown",fe),window.removeEventListener("beforeunload",R),v?.removeEventListener("wheel",xe),E.forEach(e=>e.cancel()),h&&h.destroy()}),tt(()=>{i.pdfUrl&&i.pdfUrl!==W&&(W=i.pdfUrl,B())});var j=ht();dt("1e57rkc",e=>{var n=ct();D(e,n)});var G=w(j),X=w(G),Q=w(X);Q.__click=he;var $=x(Q,2),T=w($);ot(T),T.__change=ue;var ve=x(T,2),je=w(ve);m(ve),m($);var we=x($,2);we.__click=ge,m(X);var ee=x(X,4),te=w(ee);te.__click=()=>pe("single");var me=x(te,2);me.__click=()=>pe("scroll"),m(ee);var re=x(ee,4),be=w(re);be.__click=Y;var ae=x(be,2),Ve=w(ae);m(ae);var Oe=x(ae,2);Oe.__click=q,m(re);var ye=x(re,4),ke=w(ye);ke.__click=Ie;var ze=x(ke,2);ze.__click=Ue,m(ye),at(2),m(G);var M=x(G,2);M.__mouseup=We;var Be=w(M);{var Ae=e=>{var n=ut();D(e,n)},He=e=>{var n=Ne(),t=nt(n);{var a=s=>{var o=gt(),l=x(w(o),4),f=w(l,!0);m(l);var k=x(l,2);k.__click=B,m(o),Se(()=>se(f,r(I))),D(s,o)};_e(t,s=>{r(I)&&s(a)},!0)}D(e,n)};_e(Be,e=>{r(z)?e(Ae):e(He,!1)})}m(M),Le(M,e=>d=e,()=>d),m(j),Le(j,e=>v=e,()=>v),Se(e=>{Q.disabled=r(c)<=1,se(je,`/ ${r(g)??""}`),we.disabled=r(c)>=r(g),Me(te,1,`rounded px-2 py-1 text-sm transition-colors ${r(b)==="single"?"bg-neutral-200 font-medium text-neutral-800 dark:bg-neutral-600 dark:text-white":"text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"}`),Me(me,1,`rounded px-2 py-1 text-sm transition-colors ${r(b)==="scroll"?"bg-neutral-200 font-medium text-neutral-800 dark:bg-neutral-600 dark:text-white":"text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"}`),se(Ve,`${e??""}%`)},[()=>Math.round(r(p)*100)]),Te("blur",T,ue),lt(T,()=>r(L),e=>u(L,e)),Te("scroll",M,Re),D(O,j),rt()}it(["click","change","mouseup"]);export{bt as default};
