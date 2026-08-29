/* Simple Skin — site behaviour.
   Plain ES modules-free JS so the site deploys as static files with no build step. */
(function () {
  "use strict";

  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------------- sticky header shadow ---------------- */
  var head = document.querySelector(".site-head");
  if (head) {
    var onScroll = function () { head.classList.toggle("stuck", window.scrollY > 8); };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  /* ---------------- mobile nav ---------------- */
  var toggle = document.querySelector(".nav-toggle");
  var links = document.querySelector(".nav-links");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      var open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
    });
  }

  /* ---------------- floating pixels ---------------- */
  var fx = document.querySelector(".bg-fx");
  if (fx && !reduced) {
    for (var i = 0; i < 18; i++) {
      var p = document.createElement("span");
      p.className = "pixel";
      p.style.left = (Math.random() * 100).toFixed(2) + "%";
      p.style.animationDuration = (16 + Math.random() * 22).toFixed(1) + "s";
      p.style.animationDelay = (-Math.random() * 30).toFixed(1) + "s";
      var size = 6 + Math.round(Math.random() * 12);
      p.style.width = p.style.height = size + "px";
      p.style.opacity = (0.06 + Math.random() * 0.16).toFixed(2);
      fx.appendChild(p);
    }
  }

  /* ---------------- scroll reveal ---------------- */
  var revealables = document.querySelectorAll(".reveal");
  if (revealables.length) {
    if (!("IntersectionObserver" in window) || reduced) {
      revealables.forEach(function (el) { el.classList.add("in"); });
    } else {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          // Stagger siblings a little so a row of cards arrives in sequence.
          var delay = Number(entry.target.dataset.delay || 0);
          setTimeout(function () { entry.target.classList.add("in"); }, delay);
          io.unobserve(entry.target);
        });
      }, { rootMargin: "0px 0px -8% 0px", threshold: 0.05 });
      revealables.forEach(function (el) { io.observe(el); });

      // Safety net: web fonts and images can reflow the page after the observer first
      // ran, which could leave something already on screen stuck invisible.
      var sweep = function () {
        revealables.forEach(function (el) {
          if (el.classList.contains("in")) return;
          var r = el.getBoundingClientRect();
          if (r.top < window.innerHeight && r.bottom > 0) {
            el.classList.add("in");
            io.unobserve(el);
          }
        });
      };
      window.addEventListener("load", function () { setTimeout(sweep, 250); });
      setTimeout(sweep, 1200);
    }
  }

  /* ---------------- slideshow ---------------- */
  var shots = document.querySelector("[data-slideshow]");
  if (shots) {
    var slides = Array.prototype.slice.call(shots.querySelectorAll(".slide"));
    var thumbs = Array.prototype.slice.call(shots.querySelectorAll(".thumb"));
    var capNum = shots.querySelector("[data-cap-num]");
    var capTitle = shots.querySelector("[data-cap-title]");
    var capText = shots.querySelector("[data-cap-text]");
    var stage = shots.querySelector(".stage");
    var index = 0;
    var timer = null;
    var DWELL = 7000;

    var show = function (next, restart) {
      index = (next + slides.length) % slides.length;
      slides.forEach(function (s, i) { s.classList.toggle("on", i === index); });
      thumbs.forEach(function (t, i) { t.setAttribute("aria-selected", String(i === index)); });
      var active = slides[index];
      if (capNum) capNum.textContent = String(index + 1).padStart(2, "0") + " / " + String(slides.length).padStart(2, "0");
      if (capTitle) capTitle.textContent = active.dataset.title || "";
      if (capText) capText.textContent = active.dataset.text || "";
      if (restart !== false) autoplay();
    };

    var autoplay = function () {
      clearTimeout(timer);
      if (reduced) { shots.classList.remove("playing"); return; }
      // Retrigger the thumb progress bar by replaying the class.
      shots.classList.remove("playing");
      void shots.offsetWidth;
      shots.classList.add("playing");
      timer = setTimeout(function () { show(index + 1); }, DWELL);
    };

    var stop = function () { clearTimeout(timer); shots.classList.remove("playing"); };

    thumbs.forEach(function (t, i) {
      t.addEventListener("click", function () { show(i); });
    });
    var prev = shots.querySelector(".stage-nav.prev");
    var next = shots.querySelector(".stage-nav.next");
    if (prev) prev.addEventListener("click", function () { show(index - 1); });
    if (next) next.addEventListener("click", function () { show(index + 1); });

    // Clicking the picture itself advances, which is what most people try first.
    if (stage) {
      stage.addEventListener("click", function (event) {
        if (event.target.closest(".stage-nav")) return;
        show(index + 1);
      });
    }

    shots.addEventListener("mouseenter", stop);
    shots.addEventListener("mouseleave", autoplay);
    shots.addEventListener("focusin", stop);

    shots.setAttribute("tabindex", "0");
    shots.addEventListener("keydown", function (event) {
      if (event.key === "ArrowLeft") { event.preventDefault(); show(index - 1); }
      if (event.key === "ArrowRight") { event.preventDefault(); show(index + 1); }
    });

    // Swipe on touch devices.
    var startX = null;
    stage.addEventListener("touchstart", function (e) { startX = e.touches[0].clientX; stop(); }, { passive: true });
    stage.addEventListener("touchend", function (e) {
      if (startX === null) return;
      var dx = e.changedTouches[0].clientX - startX;
      if (Math.abs(dx) > 40) show(index + (dx < 0 ? 1 : -1));
      else autoplay();
      startX = null;
    });

    // Pause while the tab is hidden so the timer does not race ahead.
    document.addEventListener("visibilitychange", function () {
      if (document.hidden) stop(); else autoplay();
    });

    show(0);
  }

  /* ---------------- current year ---------------- */
  document.querySelectorAll("[data-year]").forEach(function (el) {
    el.textContent = String(new Date().getFullYear());
  });
})();
