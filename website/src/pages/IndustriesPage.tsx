import {Navigate, useParams} from "react-router-dom";
import {industryExperiences} from "../landing/features-section/featureContent.ts";
import {PageShell} from "../shared/PageShell.tsx";
import {IndustriesPageDetails} from "./IndustriesPageDetails.tsx";
import {IndustriesPageFinalCta} from "./IndustriesPageFinalCta.tsx";
import {IndustriesPageHero} from "./IndustriesPageHero.tsx";
import {industries} from "./IndustriesPageContent.ts";

export function IndustriesPage()
{
    const {industry} = useParams<{industry: string}>();

    if (!industry || !industries[industry])
    {
        return <Navigate to="/" replace/>;
    }

    const data = industries[industry];
    const demoIndustry = industryExperiences.find((item) => item.slug === industry);

    if (!demoIndustry)
    {
        return <Navigate to="/" replace/>;
    }

    return (
        <PageShell>
            <IndustriesPageHero
                data={data}
                demoIndustry={demoIndustry}
            />
            <IndustriesPageDetails data={data}/>
            <IndustriesPageFinalCta/>
        </PageShell>
    );
}
