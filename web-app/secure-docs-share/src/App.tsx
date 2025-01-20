import React from 'react';
import {BrowserRouter, Route, Routes} from 'react-router-dom';
import Home from './app/home';
import SignIn from './app/sign-in';
import CompanyRegistration from './app/company-registration';
import {AuthProvider} from './context/AuthContext';
import './App.css';
import ProtectedRoute from "./app/components/ProtectedRoutes.tsx";
import Landing from "./app/landing/Landing.tsx";
import SignUp from "./app/sign-up";
import RedirectIfAuthenticated from "./app/components/RedirectIfAuthenticated.tsx";
import NotFound from './app/NotFound.tsx';
import Onboarding from "./app/onboarding/Onboarding.tsx";

const App: React.FC = () => {
    return (
        <BrowserRouter>
        <AuthProvider>
                <Routes>
                    <Route path="/" element={<Home />} />
                    <Route path="/sign-in" element={<RedirectIfAuthenticated element={<SignIn />} />} />
                    <Route path="/sign-up" element={<RedirectIfAuthenticated element={<SignUp />} />} />
                    <Route path="/onboarding/individual-registration"
                           element={<ProtectedRoute path='/sign-in' element={<Onboarding/>}/>}/>
                    <Route path="/onboarding/company-registration"
                           element={<ProtectedRoute path='/sign-in' element={<CompanyRegistration/>}/>}/>
                    <Route path="/landing" element={<ProtectedRoute path='/sign-in' element={<Landing />} />} />
                    <Route path="*" element={<NotFound/>}/>
                </Routes>
        </AuthProvider>
        </BrowserRouter>
    );
};

export default App;