import {Routes, Route, Navigate} from "react-router-dom";
import {LandingHeader} from "./landing/LandingHeader.tsx";
import {HeroSection} from "./landing/HeroSection.tsx";
import {RisksSection} from "./landing/RisksSection.tsx";
import {FeaturesSection} from "./landing/FeaturesSection.tsx";
import {AudienceSection} from "./landing/AudienceSection.tsx";
import {FooterCtaSection} from "./landing/FooterCtaSection.tsx";
import {appStyles} from "./AppStyles.tsx";
import {IdpSetupGuidePage} from "./pages/IdpSetupGuidePage.tsx";
import {SecurityFaqPage} from "./pages/SecurityFaqPage.tsx";

function LandingPage()
{
    const styles = appStyles();

    return (
        <div className={styles.page}>
            <LandingHeader/>
            <main>
                <HeroSection/>
                <FeaturesSection/>
                <RisksSection/>
                <AudienceSection/>
            </main>
            <FooterCtaSection/>
        </div>
    );
}

export default function App()
{
    return (
        <Routes>
            <Route path="/" element={<LandingPage/>}/>
            <Route path="/help/idp-setup" element={<IdpSetupGuidePage/>}/>
            <Route path="/help/security-faq" element={<SecurityFaqPage/>}/>
            <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
    );
}
