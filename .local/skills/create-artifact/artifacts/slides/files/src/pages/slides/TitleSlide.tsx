export default function TitleSlide() {
  return (
    <div className="relative h-screen w-screen overflow-hidden bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 text-slate-100">
      <div className="absolute inset-0 opacity-25 [background:radial-gradient(circle_at_20%_20%,rgba(99,102,241,0.65),transparent_45%),radial-gradient(circle_at_80%_75%,rgba(14,165,233,0.45),transparent_40%)]" />
      <div className="relative flex h-full flex-col justify-between px-[7vw] py-[8vh]">
        <div className="text-[1.5vw] tracking-[0.18em] text-indigo-200/80 uppercase">
          Slide Deck Template
        </div>
        <div className="max-w-[70vw]">
          <h1 className="text-[6vw] leading-[0.95] font-extrabold tracking-tight">
            Presentation Title
          </h1>
          <p className="mt-[2.5vh] max-w-[56vw] text-[2vw] leading-snug text-slate-200/85">
            Add a concise subtitle that explains the story this deck will tell.
          </p>
        </div>
        <div className="text-[1.4vw] text-slate-200/70">
          Prepared by Team Name • Month YYYY
        </div>
      </div>
    </div>
  );
}
