package ar.com.flyzar.tvdisplay;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

/**
 * Ciclo automatico v1.2
 *
 * Fase A - fondo MY ROUTE:
 *   Cockpit 30s / Location Indicator 30s / World Clock 30s / solo My Route 4 min
 * Fase B - fondo GENERAL VIEW:
 *   idem, y 4 min de General View sola
 * Repite indefinidamente.
 *
 * Los controles se buscan por su etiqueta visible, no por coordenadas fijas,
 * asi que un cambio de layout de FlightPath3D no lo rompe automaticamente.
 */
public class AutoCycle {

    public interface Log {
        void onStep(String message);
    }

    /* --------- tiempos (milisegundos) --------- */
    private static final long OVERLAY_MS = 30_000L;   // cada panel abierto
    private static final long BASE_MS = 240_000L;     // 4 minutos de fondo limpio
    private static final long SETTLE_MS = 1_500L;     // respiro entre acciones

    /* --------- pasos del ciclo --------- */
    private static final String[][] STEPS = {
            // {accion, argumento, duracion}
            {"base_route", "", String.valueOf(SETTLE_MS)},
            {"open", "cockpit", String.valueOf(OVERLAY_MS)},
            {"close", "cockpit", String.valueOf(SETTLE_MS)},
            {"open", "location", String.valueOf(OVERLAY_MS)},
            {"close", "location", String.valueOf(SETTLE_MS)},
            {"open", "clock", String.valueOf(OVERLAY_MS)},
            {"close", "clock", String.valueOf(SETTLE_MS)},
            {"wait", "solo My Route", String.valueOf(BASE_MS)},

            {"base_general", "", String.valueOf(SETTLE_MS)},
            {"open", "cockpit", String.valueOf(OVERLAY_MS)},
            {"close", "cockpit", String.valueOf(SETTLE_MS)},
            {"open", "location", String.valueOf(OVERLAY_MS)},
            {"close", "location", String.valueOf(SETTLE_MS)},
            {"open", "clock", String.valueOf(OVERLAY_MS)},
            {"close", "clock", String.valueOf(SETTLE_MS)},
            {"wait", "solo General View", String.valueOf(BASE_MS)},
    };

    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Log log;

    private boolean running = false;
    private int index = 0;
    private int generation = 0;

    public AutoCycle(WebView web, Log log) {
        this.web = web;
        this.log = log;
    }

    public boolean isRunning() {
        return running;
    }

    /** Arranca desde cero. Se llama cuando termina de cargar la pagina. */
    public void start() {
        stop();
        running = true;
        index = 0;
        final int myGen = ++generation;
        // damos unos segundos a que el mapa termine de dibujarse
        handler.postDelayed(() -> {
            if (running && myGen == generation) runStep(myGen);
        }, 8_000L);
    }

    public void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
    }

    public void toggle() {
        if (running) {
            stop();
            if (log != null) log.onStep("Ciclo pausado");
        } else {
            if (log != null) log.onStep("Ciclo reanudado");
            start();
        }
    }

    private void runStep(final int myGen) {
        if (!running || myGen != generation) return;

        String[] step = STEPS[index % STEPS.length];
        String action = step[0];
        String arg = step[1];
        long duration = Long.parseLong(step[2]);

        switch (action) {
            case "base_route":
                eval("__fz.baseMyRoute()", "Fondo: My Route");
                break;
            case "base_general":
                eval("__fz.baseGeneralView()", "Fondo: General View");
                break;
            case "open":
                eval("__fz.open('" + arg + "')", "Abriendo " + arg);
                break;
            case "close":
                eval("__fz.closeOverlay()", "Cerrando " + arg);
                break;
            default:
                if (log != null) log.onStep(arg + " (" + (duration / 60000) + " min)");
                break;
        }

        index = (index + 1) % STEPS.length;
        handler.postDelayed(() -> runStep(myGen), duration);
    }

    private void eval(String call, String description) {
        if (log != null) log.onStep(description);
        web.evaluateJavascript(JS + "\ntry{" + call + "}catch(e){'error: '+e}", value -> {
            if (log != null && value != null && value.contains("miss")) {
                log.onStep(description + " -> no encontrado");
            }
        });
    }

    /* ------------------------------------------------------------------
     * Libreria JS inyectada. Se reinyecta en cada llamada por si la pagina
     * hizo una navegacion interna y limpio el contexto.
     * ------------------------------------------------------------------ */
    private static final String JS = """
        (function(){
        if (window.__fz && window.__fz.v === 12) return;
        var F = {};
        F.v = 12;

        F.labels = {
          cockpit:  ['cockpit','cabina','cockpit view','vista de cabina'],
          location: ['location indicator','location','indicador de posicion','indicador de ubicacion','position'],
          clock:    ['world clock','clock','reloj mundial','reloj','time zones'],
          route:    ['my route','mi ruta','route','ruta'],
          general:  ['general view','vista general','world view','globe','global view']
        };

        F.norm = function(s){
          return (s||'').replace(/\\s+/g,' ').trim().toLowerCase();
        };

        F.visible = function(e){
          if(!e || !e.getBoundingClientRect) return false;
          var r = e.getBoundingClientRect();
          if(r.width < 6 || r.height < 6) return false;
          if(r.bottom < 0 || r.top > innerHeight) return false;
          var s = getComputedStyle(e);
          if(s.visibility === 'hidden' || s.display === 'none') return false;
          if(parseFloat(s.opacity || '1') < 0.05) return false;
          return true;
        };

        F.text = function(e){
          var parts = [
            e.getAttribute && e.getAttribute('aria-label'),
            e.getAttribute && e.getAttribute('title'),
            e.getAttribute && e.getAttribute('alt'),
            e.getAttribute && e.getAttribute('data-label'),
            e.textContent
          ];
          return F.norm(parts.filter(Boolean).join(' '));
        };

        F.tap = function(e){
          try {
            var r = e.getBoundingClientRect();
            var x = r.left + r.width/2, y = r.top + r.height/2;
            var opts = {bubbles:true, cancelable:true, clientX:x, clientY:y, view:window};
            ['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(t){
              var Ev = (t.indexOf('pointer')===0 && window.PointerEvent) ? PointerEvent : MouseEvent;
              e.dispatchEvent(new Ev(t, opts));
            });
            if (typeof e.click === 'function') e.click();
            return true;
          } catch(err){ return false; }
        };

        F.clickable = function(e){
          var n = e;
          for (var i=0; i<4 && n; i++){
            var tag = (n.tagName||'').toLowerCase();
            if (tag==='button' || tag==='a' || n.getAttribute('role')==='button'
                || n.onclick || (n.className+'').indexOf('btn')>=0) return n;
            n = n.parentElement;
          }
          return e;
        };

        /* busca el elemento visible mas chico cuyo texto coincida */
        F.find = function(labels){
          var nodes = document.querySelectorAll('button,a,[role="button"],[role="tab"],[role="menuitem"],li,div,span,img,p,h1,h2,h3');
          var best = null, bestArea = Infinity;
          for (var i=0; i<nodes.length; i++){
            var e = nodes[i];
            if (!F.visible(e)) continue;
            var t = F.text(e);
            if (!t || t.length > 60) continue;
            var hit = false;
            for (var j=0; j<labels.length; j++){
              var L = labels[j];
              if (t === L || (t.indexOf(L) >= 0 && t.length <= L.length + 14)) { hit = true; break; }
            }
            if (!hit) continue;
            var r = e.getBoundingClientRect();
            var area = r.width * r.height;
            if (area < bestArea){ bestArea = area; best = e; }
          }
          return best;
        };

        F.clickLabel = function(labels){
          var e = F.find(labels);
          if (!e) return false;
          return F.tap(F.clickable(e));
        };

        F.open = function(key){
          var ok = F.clickLabel(F.labels[key] || []);
          return ok ? 'ok' : 'miss';
        };

        F.baseMyRoute = function(){
          return F.clickLabel(F.labels.route) ? 'ok' : 'miss';
        };

        /* General View: primero por etiqueta, si no por el icono de avion
           abajo a la izquierda */
        F.baseGeneralView = function(){
          if (F.clickLabel(F.labels.general)) return 'ok';
          var nodes = document.querySelectorAll('img,svg,button,[role="button"],div');
          var best = null, bestScore = Infinity;
          for (var i=0; i<nodes.length; i++){
            var e = nodes[i];
            if (!F.visible(e)) continue;
            var r = e.getBoundingClientRect();
            var area = r.width * r.height;
            if (area < 200 || area > 40000) continue;
            if (r.left > innerWidth * 0.30) continue;
            if (r.bottom < innerHeight * 0.60) continue;
            var score = r.left + (innerHeight - r.bottom);
            if (score < bestScore){ bestScore = score; best = e; }
          }
          if (!best) return 'miss';
          F.tap(F.clickable(best));
          return 'ok-heuristic';
        };

        /* cierra un panel sin tocar la vista de fondo */
        F.closeOverlay = function(){
          document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,bubbles:true}));
          var closers = ['close','cerrar','dismiss','done','listo','back','volver'];
          var e = F.find(closers);
          if (e) { F.tap(F.clickable(e)); return 'ok'; }
          var x = F.find(['x', String.fromCharCode(215)]);
          if (x) { F.tap(F.clickable(x)); return 'ok-x'; }
          return 'ok-esc';
        };

        window.__fz = F;
        })();
        """;
}
