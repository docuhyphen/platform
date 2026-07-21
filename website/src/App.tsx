import {Routes, Route} from "react-router-dom";
import {useState, useSyncExternalStore} from "react";
import {LandingHeader} from "./landing/landing-header/LandingHeader.tsx";
import {HeroSection} from "./landing/hero-section/HeroSection.tsx";
import {RisksSection} from "./landing/RisksSection.tsx";
import {HowItWorksSection} from "./landing/how-it-works-section/HowItWorksSection.tsx";
import {FeaturesSection} from "./landing/features-section/FeaturesSection.tsx";
import {AudienceSection} from "./landing/AudienceSection.tsx";
import {FinalCtaSection} from "./landing/final-cta-section/FinalCtaSection.tsx";
import {IndustryPickerDialog} from "./landing/IndustryPickerDialog.tsx";
import {getStoredIndustry} from "./landing/industryOptions.ts";
import type {IndustrySlug} from "./landing/industryOptions.ts";
import {appStyles} from "./AppStyles.tsx";
import {PricingPage} from "./pages/PricingPage.tsx";
import {IndustriesPage} from "./pages/IndustriesPage.tsx";
import {SecurityPage} from "./pages/security-page/SecurityPage.tsx";
import {AboutPage} from "./pages/about-page/AboutPage.tsx";
import {ContactPage} from "./pages/ContactPage.tsx";
import {NotFoundPage} from "./pages/NotFoundPage.tsx";
import {Seo} from "./seo/Seo.tsx";

function LandingPage({industrySlug}: {industrySlug: IndustrySlug | null})
{
    const styles = appStyles();

    return (
        <div className={styles.page}>
            <LandingHeader fixed/>
            <main
                id="home-main-content"
                className={styles.mainContent}
            >
                <HeroSection/>
                <div
                    id="home-features-surface"
                    className={styles.featuresSurface}
                >
                    <FeaturesSection initialIndustrySlug={industrySlug ?? undefined}/>
                </div>
                <HowItWorksSection/>
                <div
                    id="home-risks-surface"
                    className={styles.risksSurface}
                >
                    <RisksSection/>
                </div>
                <div
                    id="home-audience-surface"
                    className={styles.audienceSurface}
                >
                    <AudienceSection/>
                </div>
                {/*<PricingTeaserSection/>*/}
            </main>
            <FinalCtaSection/>
        </div>
    );
}

export default function App()
{
    const storedIndustrySlug = useSyncExternalStore(
        () => () => undefined,
        getStoredIndustry,
        () => null,
    );
    const [selectedIndustrySlug, setSelectedIndustrySlug] = useState<IndustrySlug | null>(null);
    const industrySlug = selectedIndustrySlug ?? storedIndustrySlug;
    const showPicker = industrySlug === null;

    return (
        <>
            <Seo/>
            {showPicker && (
                <IndustryPickerDialog onSelect={(slug) => setSelectedIndustrySlug(slug)}/>
            )}
            <Routes>
                <Route path="/" element={<LandingPage industrySlug={industrySlug}/>}/>
            <Route path="/pricing" element={<PricingPage/>}/>
            <Route path="/industries/:industry" element={<IndustriesPage/>}/>
            <Route path="/security" element={<SecurityPage/>}/>
            <Route path="/about" element={<AboutPage/>}/>
            <Route path="/contact" element={<ContactPage/>}/>
            <Route path="*" element={<NotFoundPage/>}/>
            </Routes>
        </>
    );
}
