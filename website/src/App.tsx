import {Routes, Route} from "react-router-dom";
import {useState, useSyncExternalStore} from "react";
import {LandingHeader} from "./landing/landing-header/LandingHeader.tsx";
import {HeroSection} from "./landing/hero-section/HeroSection.tsx";
import {RisksSection} from "./landing/risks-section/RisksSection.tsx";
import {HowItWorksSection} from "./landing/how-it-works-section/HowItWorksSection.tsx";
import {FeaturesSection} from "./landing/features-section/FeaturesSection.tsx";
import {AudienceSection} from "./landing/audience-section/AudienceSection.tsx";
import {ContactInfoSection} from "./landing/contact-info-section/ContactInfoSection.tsx";
import {IndustryPickerDialog} from "./landing/industry-picker-dialog/IndustryPickerDialog.tsx";
import {getStoredIndustry} from "./landing/industryOptions.ts";
import type {IndustrySlug} from "./landing/industryOptions.ts";
import {appStyles} from "./AppStyles.tsx";
import {PricingPage} from "./pages/pricing-page/PricingPage.tsx";
import {IndustriesPage} from "./pages/industries-page/IndustriesPage.tsx";
import {SecurityPage} from "./pages/security-page/SecurityPage.tsx";
import {LegalDocumentPage} from "./pages/legal-document-page/LegalDocumentPage.tsx";
import {
    privacyPolicyDocument,
    termsOfServiceDocument,
} from "./pages/legal-document-page/legalDocuments.ts";
import {NotFoundPage} from "./pages/not-found-page/NotFoundPage.tsx";
import {Seo} from "./seo/Seo.tsx";
import {ScrollToHashHandler} from "./shared/ScrollToHashHandler.tsx";
import {SiteFooter} from "./shared/site-footer/SiteFooter.tsx";

function LandingPage({industrySlug}: {industrySlug: IndustrySlug | null})
{
    const styles = appStyles();

    return (
        <div id={"app-div-1"}
             className={styles.page}>
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
                <div
                    id="home-how-it-works-surface"
                    className={styles.mobileSectionGap}
                >
                    <HowItWorksSection/>
                </div>
                <div
                    id="home-risks-surface"
                    className={`${styles.risksSurface} ${styles.mobileSectionGap}`}
                >
                    <RisksSection/>
                </div>
                <div
                    id="home-audience-surface"
                    className={`${styles.audienceSurface} ${styles.mobileSectionGap}`}
                >
                    <AudienceSection/>
                </div>
            </main>
            <div
                id="home-contact-info-surface"
                className={styles.mobileSectionGap}
            >
                <ContactInfoSection/>
            </div>
            <SiteFooter/>
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
            <ScrollToHashHandler/>
            {showPicker && (
                <IndustryPickerDialog onSelect={(slug) => setSelectedIndustrySlug(slug)}/>
            )}
            <Routes>
                <Route path="/"
                       element={<LandingPage industrySlug={industrySlug}/>}/>
                <Route path="/pricing"
                       element={<PricingPage/>}/>
                <Route path="/industries/:industry"
                       element={<IndustriesPage/>}/>
                <Route path="/security"
                       element={<SecurityPage/>}/>
                <Route
                    path="/privacy-policy"
                    element={<LegalDocumentPage document={privacyPolicyDocument}/>}
                />
                <Route
                    path="/terms-of-service"
                    element={<LegalDocumentPage document={termsOfServiceDocument}/>}
                />
                <Route path="*"
                       element={<NotFoundPage/>}/>
            </Routes>
        </>
    );
}
