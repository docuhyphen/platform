import {Routes, Route} from "react-router-dom";
import {useState} from "react";
import {LandingHeader} from "./landing/LandingHeader.tsx";
import {HeroSection} from "./landing/HeroSection.tsx";
import {RisksSection} from "./landing/RisksSection.tsx";
import {FeaturesSection} from "./landing/FeaturesSection.tsx";
import {AudienceSection} from "./landing/AudienceSection.tsx";
import {FooterCtaSection} from "./landing/FooterCtaSection.tsx";
import {IndustryPickerDialog, getStoredIndustry} from "./landing/IndustryPickerDialog.tsx";
import type {IndustrySlug} from "./landing/IndustryPickerDialog.tsx";
import {Footer} from "./shared/Footer.tsx";
import {appStyles} from "./AppStyles.tsx";
import {SecurityFaqPage} from "./pages/SecurityFaqPage.tsx";
import {PricingPage} from "./pages/PricingPage.tsx";
import {SolutionsPage} from "./pages/SolutionsPage.tsx";
import {ResourcesPage} from "./pages/ResourcesPage.tsx";
import {SecurityPage} from "./pages/SecurityPage.tsx";
import {AboutPage} from "./pages/AboutPage.tsx";
import {ContactPage} from "./pages/ContactPage.tsx";
import {NotFoundPage} from "./pages/NotFoundPage.tsx";

function LandingPage({industrySlug}: {industrySlug: IndustrySlug | null})
{
    const styles = appStyles();

    return (
        <div className={styles.page}>
            <LandingHeader/>
            <main>
                <HeroSection/>
                <FeaturesSection initialIndustrySlug={industrySlug ?? undefined}/>
                <RisksSection/>
                <AudienceSection/>
                {/*<PricingTeaserSection/>*/}
            </main>
            <FooterCtaSection/>
            <Footer/>
        </div>
    );
}

export default function App()
{
    const [industrySlug, setIndustrySlug] = useState<IndustrySlug | null>(() => getStoredIndustry());
    const showPicker = industrySlug === null;

    return (
        <>
            {showPicker && (
                <IndustryPickerDialog onSelect={(slug) => setIndustrySlug(slug)}/>
            )}
            <Routes>
                <Route path="/" element={<LandingPage industrySlug={industrySlug}/>}/>
            <Route path="/pricing" element={<PricingPage/>}/>
            <Route path="/solutions/:industry" element={<SolutionsPage/>}/>
            <Route path="/resources" element={<ResourcesPage/>}/>
            <Route path="/security" element={<SecurityPage/>}/>
            <Route path="/about" element={<AboutPage/>}/>
            <Route path="/contact" element={<ContactPage/>}/>
            <Route path="/help/security-faq" element={<SecurityFaqPage/>}/>
            <Route path="*" element={<NotFoundPage/>}/>
            </Routes>
        </>
    );
}
