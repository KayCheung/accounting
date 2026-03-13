export default function Agenda() {
  return (
    <div className="relative h-screen w-screen overflow-hidden bg-slate-50 text-slate-900">
      <div className="absolute right-0 top-0 h-[36vh] w-[44vw] rounded-bl-[12vw] bg-indigo-100/70" />
      <div className="relative flex h-full flex-col px-[7vw] py-[8vh]">
        <h2 className="text-[4vw] leading-tight font-bold tracking-tight">
          Agenda
        </h2>
        <p className="mt-[1.2vh] max-w-[52vw] text-[1.8vw] text-slate-600">
          Outline the narrative sequence before diving into detail.
        </p>
        <ol className="mt-[6vh] grid gap-[2.3vh] text-[2vw] leading-tight">
          <li className="flex items-start gap-[1.4vw]">
            <span className="inline-flex h-[3.4vw] w-[3.4vw] items-center justify-center rounded-full bg-indigo-600 text-[1.6vw] font-semibold text-white">
              1
            </span>
            <span className="pt-[0.3vh]">Context and objective</span>
          </li>
          <li className="flex items-start gap-[1.4vw]">
            <span className="inline-flex h-[3.4vw] w-[3.4vw] items-center justify-center rounded-full bg-indigo-600 text-[1.6vw] font-semibold text-white">
              2
            </span>
            <span className="pt-[0.3vh]">Current-state findings</span>
          </li>
          <li className="flex items-start gap-[1.4vw]">
            <span className="inline-flex h-[3.4vw] w-[3.4vw] items-center justify-center rounded-full bg-indigo-600 text-[1.6vw] font-semibold text-white">
              3
            </span>
            <span className="pt-[0.3vh]">Strategic priorities</span>
          </li>
          <li className="flex items-start gap-[1.4vw]">
            <span className="inline-flex h-[3.4vw] w-[3.4vw] items-center justify-center rounded-full bg-indigo-600 text-[1.6vw] font-semibold text-white">
              4
            </span>
            <span className="pt-[0.3vh]">Execution roadmap</span>
          </li>
          <li className="flex items-start gap-[1.4vw]">
            <span className="inline-flex h-[3.4vw] w-[3.4vw] items-center justify-center rounded-full bg-indigo-600 text-[1.6vw] font-semibold text-white">
              5
            </span>
            <span className="pt-[0.3vh]">Decision and next steps</span>
          </li>
        </ol>
      </div>
    </div>
  );
}
