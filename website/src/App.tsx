import {LandingHeader} from "./landing/LandingHeader.tsx";
import {HeroSection} from "./landing/HeroSection.tsx";
import {RisksSection} from "./landing/RisksSection.tsx";
import {FeaturesSection} from "./landing/FeaturesSection.tsx";
import {AudienceSection} from "./landing/AudienceSection.tsx";
import {FooterCtaSection} from "./landing/FooterCtaSection.tsx";
import {appStyles} from "./AppStyles.tsx";

export default function App()
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