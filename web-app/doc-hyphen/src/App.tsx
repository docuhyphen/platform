import React from 'react';
import {BrowserRouter, Route, Routes} from 'react-router-dom';
import Home from './app/home';
import SignIn from './app/authorization/sign-in';
import CompanyRegistrationPending from './app/company-registration';
import {AuthProvider} from './context/AuthContext';
import ProtectedRoute from "./app/components/ProtectedRoutes.tsx";
import Landing from "./app/landing/Landing.tsx";
import SignUp from "./app/authorization/sign-up";
import RedirectIfAuthenticated from "./app/components/RedirectIfAuthenticated.tsx";
import NotFound from './app/NotFound.tsx';
import Onboarding from "./app/onboarding/Onboarding.tsx";
import Settings from "./app/settings/Settings.tsx";
import CompanyRegistration from "./app/company-registration-pending/CompanyRegistration.tsx";
import AccountRecovery from "./app/authorization/account-recovery";

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
                               <RedirectIfAuthenticated element={<SignIn/>}/>
                           }/>

                    <Route path="/sign-up"
                           element={
                               <RedirectIfAuthenticated element={<SignUp/>
                               }/>
                           }/>

                    <Route path="/account-recovery"
                           element={
                               <RedirectIfAuthenticated element={
                                   <AccountRecovery/>
                               }/>
                           }/>

                    <Route path="/onboarding/individual-registration"
                           element={
                               <ProtectedRoute path='/sign-in' element={<Onboarding/>
                               }/>
                           }/>

                    <Route path="/onboarding/company-registration"
                           element={
                               <ProtectedRoute path='/sign-in' element={
                                   <CompanyRegistration/>
                               }/>
                           }/>

                    <Route path="/onboarding/company-registration-pending"
                           element={
                               <ProtectedRoute path='/sign-in' element={<CompanyRegistrationPending/>}/>
                           }/>



                    <Route path="/landing"
                           element={
                               <ProtectedRoute path='/sign-in' element={
                                   <Landing/>
                               }/>
                           }/>

                    <Route path="/settings"
                           element={
                               <ProtectedRoute path='/sign-in' element={<Settings/>}/>
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