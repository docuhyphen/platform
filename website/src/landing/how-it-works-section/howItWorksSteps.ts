export type HowItWorksIllustrationClass =
    | "illustrationOne"
    | "illustrationTwo"
    | "illustrationThree"
    | "illustrationFour";

export type HowItWorksAnimationClass =
    | "stepFirst"
    | "stepSecond"
    | "stepThird"
    | "stepFourth";

export type HowItWorksStep = {
    number: string;
    title: string;
    description: string;
    illustrationLabel: string;
    illustrationClass: HowItWorksIllustrationClass;
    animationClass: HowItWorksAnimationClass;
};

export const STEP_ROTATION_INTERVAL_MS = 8000;

export const howItWorksSteps: HowItWorksStep[] = [
    {
        number: "01",
        title: "Create an Exchange",
        description: "Bring together the documents, people, and context for a single piece of work.",
        illustrationLabel: "A secure document Exchange being created",
        illustrationClass: "illustrationOne",
        animationClass: "stepFirst",
    },
    {
        number: "02",
        title: "Set access and workflow",
        description: "Assign the right roles and configure the workflow that guides each Exchange.",
        illustrationLabel: "People, access controls, and workflow steps",
        illustrationClass: "illustrationTwo",
        animationClass: "stepSecond",
    },
    {
        number: "03",
        title: "Work through the process",
        description: "Participants collaborate, review, respond, and complete the steps relevant to them.",
        illustrationLabel: "Participants collaborating on a document",
        illustrationClass: "illustrationThree",
        animationClass: "stepThird",
    },
    {
        number: "04",
        title: "Finish with a clear record",
        description: "Close the Exchange with an auditable history of activity, decisions, and outcomes.",
        illustrationLabel: "A completed Exchange with an activity record",
        illustrationClass: "illustrationFour",
        animationClass: "stepFourth",
    },
];

export const getStepProgress = (index: number, activeStepIndex: number, activeStepProgress: number): number =>
    index < activeStepIndex ? 1 : index === activeStepIndex ? activeStepProgress : 0;
