import React from 'react';
import {BrowserRouter, Route, Routes} from 'react-router-dom';
import Home from './app/home';
import SignIn from './app/authorization/sign-in';
import {AuthProvider} from './context/AuthContext';
import SharingSessions from "./app/sharing-sessions/SharingSessions.tsx";
import SignUp from "./app/authorization/sign-up";
import RedirectIfNotAuthenticated from "./app/components/RedirectIfAuthenticated.tsx";
import NotFound from './app/NotFound.tsx';
import IndividualOnboarding from "./app/onboarding/individual-onboarding/IndividualOnboarding.tsx";
import Settings from "./app/settings/Settings.tsx";
import AccountRecovery from "./app/authorization/account-recovery";
import NoAuthSharingSession from "./app/no-auth-sharing-session/NoAuthSharingSession.tsx";
import NoMenuProtectedRoute from "./app/components/NoMenuProtectedRoutes.tsx";
import PersonRegistrationProtectedRoute from "./context/OnboardingGuard.tsx";
import {OrganizationRegistrationProtectedRoute} from "./context/OrganizationRegistrationProtectedRoute.tsx";
import OrganizationOnboarding from "./app/onboarding/organization-onboarding/OrganizationOnboarding.tsx";

const App: React.FC = () => {
    return (
        <BrowserRouter>
        <AuthProvider>
                <Routes>
                    <Route path="/"
                           element={
                               <Home/>
                           }/>

                    <Route path="/sign-in"
                           element={
                               <RedirectIfNotAuthenticated element={<SignIn/>}/>
                           }/>

                    <Route path="/sign-up"
                           element={
                               <RedirectIfNotAuthenticated element={<SignUp/>
                               }/>
                           }/>

                    <Route path="/account-recovery"
                           element={
                               <RedirectIfNotAuthenticated
                                   element={<AccountRecovery/>}/>
                           }/>

                    <Route path="/nas"
                           element={
                               <RedirectIfNotAuthenticated
                                   element={<NoAuthSharingSession/>
                                   }/>
                           }/>

                    <Route path="/onboarding/individual"
                           element={
                               <NoMenuProtectedRoute path='/sign-in'
                                                     element={<IndividualOnboarding/>}/>
                           }/>

                    <Route path="/onboarding/organization"
                           element={
                               <OrganizationRegistrationProtectedRoute
                                   element={<OrganizationOnboarding/>}/>
                           }/>

                    <Route path="/sharing-sessions"
                           element={
                               <PersonRegistrationProtectedRoute element={<SharingSessions/>}/>
                           }/>

                    <Route path="/settings"
                           element={
                               <PersonRegistrationProtectedRoute element={<Settings/>}/>
                           }/>
                    <Route path="*"
                           element={
                               <NotFound/>
                           }/>
                </Routes>
        </AuthProvider>
        </BrowserRouter>
    );
};

export default App;