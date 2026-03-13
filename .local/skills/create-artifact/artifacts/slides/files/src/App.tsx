import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { slides } from "@/slideLoader";

function getSlideIndex(pathname: string): number {
  const match = pathname.match(/^\/slide(\d+)$/);
  if (!match) return -1;
  const position = parseInt(match[1], 10);
  return slides.findIndex((s) => s.position === position);
}

function SlideEditor() {
  const location = useLocation();
  const navigate = useNavigate();
  const currentIndex = getSlideIndex(location.pathname);

  useEffect(() => {
    if (currentIndex === -1) return;

    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === " ") {
        event.preventDefault();
      }
      if ((event.key === "ArrowLeft" || event.key === "ArrowUp") && currentIndex > 0) {
        navigate(`/slide${slides[currentIndex - 1].position}`);
      }
      if (
        (event.key === "ArrowRight" || event.key === "ArrowDown" || event.key === " ") &&
        currentIndex < slides.length - 1
      ) {
        navigate(`/slide${slides[currentIndex + 1].position}`);
      }
    };

    const onClick = (event: MouseEvent) => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey) return;
      const fraction = event.clientX / window.innerWidth;
      if (fraction < 0.4 && currentIndex > 0) {
        navigate(`/slide${slides[currentIndex - 1].position}`);
      } else if (fraction >= 0.4 && currentIndex < slides.length - 1) {
        navigate(`/slide${slides[currentIndex + 1].position}`);
      }
    };

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("click", onClick);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("click", onClick);
    };
  }, [currentIndex, navigate]);

  return (
    <>
      {slides.map((slide, index) => (
        <div
          key={slide.id}
          style={{ display: index === currentIndex ? "block" : "none" }}
        >
          <slide.Component />
        </div>
      ))}
    </>
  );
}

function AllSlides() {
  return (
    <div className="bg-black">
      {slides.map((slide) => (
        <div
          key={slide.id}
          className="slide relative aspect-video overflow-hidden"
          style={{ width: "1920px", height: "1080px" }}
        >
          <div className="h-full w-full [&_.h-screen]:!h-full [&_.w-screen]:!w-full">
            <slide.Component />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();

  // DO NOT edit this useEffect
  useEffect(() => {
    if (location.pathname === "/" || getSlideIndex(location.pathname) === -1) {
      if (location.pathname !== "/allslides" && slides.length > 0) {
        navigate(`/slide${slides[0].position}`, { replace: true });
      }
    }
  }, [location.pathname, navigate]);

  // DO NOT edit this useEffect, this allows the parent frame to navigate
  // between slides via postMessage so it can avoid changing the iframe 
  // src (which causes a white flash).
  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (
        event.data?.type === "navigateToSlide" &&
        typeof event.data.position === "number" &&
        slides.some((s) => s.position === event.data.position)
      ) {
        navigate(`/slide${event.data.position}`);
      }
    };

    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [navigate]);

  if (location.pathname === "/allslides") {
    return <AllSlides />;
  }

  return <SlideEditor />;
}
