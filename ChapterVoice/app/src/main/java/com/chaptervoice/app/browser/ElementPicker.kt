package com.chaptervoice.app.browser

/**
 * JavaScript for "hide element": finds the element under a tap, marks it in red and
 * builds a CSS selector for it that is as short as possible while staying unique.
 *
 * Touches inside cross-origin ad iframes never reach the page's own scripts, so the
 * tap position comes from Android and the element is found with elementFromPoint,
 * which returns the iframe element itself.
 */
internal object ElementPicker {

    private const val HELPERS = """
        (function(){
          if (window.__cvPickReady) return;
          window.__cvPickReady = true;
          window.__cvMark = function(el){
            var o = document.getElementById('cv-mark');
            if (!o) {
              o = document.createElement('div');
              o.id = 'cv-mark';
              o.style.cssText = 'position:absolute;z-index:2147483647;pointer-events:none;' +
                'background:rgba(255,60,60,.28);outline:3px solid #ff3c3c;border-radius:2px;';
              document.documentElement.appendChild(o);
            }
            var r = el.getBoundingClientRect();
            o.style.left = (r.left + window.scrollX) + 'px';
            o.style.top = (r.top + window.scrollY) + 'px';
            o.style.width = r.width + 'px';
            o.style.height = r.height + 'px';
          };
          window.__cvUnmark = function(){
            var o = document.getElementById('cv-mark');
            if (o) o.remove();
            window.__cvEl = null;
          };
          window.__cvSel = function(el){
            function esc(s){ return window.CSS && CSS.escape ? CSS.escape(s) : s; }
            function random(s){ return /\d{3,}|[0-9a-f]{8,}/i.test(s); }
            function prefix(s){ return s.replace(/[-_]?([0-9a-f]{6,}|\d{3,}).*$/i, ''); }
            // One step of the selector. "anchor" means it names the element by id,
            // class, or ad address, not only by its position.
            function one(e){
              var t = e.tagName.toLowerCase();
              if (e.id) {
                if (!random(e.id)) return { s: '#' + esc(e.id), anchor: true, id: true };
                var pre = prefix(e.id);
                if (pre.length >= 3) return { s: t + '[id^="' + pre + '"]', anchor: true };
              }
              if (t === 'iframe' && e.src) {
                try { return { s: 'iframe[src^="' + new URL(e.src).origin + '"]', anchor: true }; } catch (x) {}
              }
              var cls = [].slice.call(e.classList).filter(function(c){ return !random(c); }).slice(0, 3);
              if (cls.length) return { s: t + '.' + cls.map(esc).join('.'), anchor: true };
              var i = 1, s = e;
              while ((s = s.previousElementSibling)) if (s.tagName === e.tagName) i++;
              return { s: t + ':nth-of-type(' + i + ')', anchor: false };
            }
            function unique(sel){
              try { return document.querySelectorAll(sel).length === 1; } catch (x) { return false; }
            }
            var parts = [], anchored = false, e = el;
            while (e && e !== document.body && e !== document.documentElement) {
              var p = one(e);
              parts.unshift(p.s);
              anchored = anchored || p.anchor;
              if (p.id) break;
              if (anchored && unique(parts.join(' > '))) break;
              e = e.parentElement;
            }
            // Reached <body> without anything identifying: use the full path from it.
            if (!anchored || e === document.body) parts.unshift('body');
            return parts.join(' > ');
          };
          window.__cvPick = function(px, py){
            var vv = window.visualViewport, s = vv ? vv.scale : 1, dpr = window.devicePixelRatio || 1;
            var x = px / dpr / s + (vv ? vv.offsetLeft : 0);
            var y = py / dpr / s + (vv ? vv.offsetTop : 0);
            var el = document.elementFromPoint(x, y);
            if (!el || el === document.body || el === document.documentElement || el.id === 'cv-mark') return '';
            window.__cvEl = el;
            __cvMark(el);
            return __cvSel(el);
          };
          window.__cvLarger = function(){
            var el = window.__cvEl;
            if (!el) return '';
            var p = el.parentElement;
            if (p && p !== document.body && p !== document.documentElement) { el = p; window.__cvEl = el; }
            __cvMark(el);
            return __cvSel(el);
          };
        })();
    """

    fun pickAt(x: Float, y: Float) = "$HELPERS; __cvPick($x, $y);"
    const val LARGER = "$HELPERS; __cvLarger();"
    const val UNMARK = "$HELPERS; __cvUnmark();"
}
