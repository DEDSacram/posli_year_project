import { lazy, Suspense } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth";
import { AlertsProvider } from "./alerts";
import Layout from "./components/Layout";
import Home from "./pages/Home";
import Search from "./pages/Search";
import ListingDetail from "./pages/ListingDetail";
import Login, { Register } from "./pages/Auth";
import Sell from "./pages/Sell";

// Routy, které nepotřebují být v počátečním balíčku: buď se používají zřídka,
// nebo jsou velké (Leaflet, chat, administrace).
const EditListing = lazy(() => import("./pages/EditListing"));
const Account = lazy(() => import("./pages/Account"));
const Deals = lazy(() => import("./pages/Deals"));
const Favorites = lazy(() => import("./pages/Favorites"));
const Profile = lazy(() => import("./pages/Profile"));
const Chat = lazy(() => import("./pages/Chat"));
const Admin = lazy(() => import("./pages/Admin"));

function Loading(): React.ReactElement {
  return (
    <div className="container mx-auto px-4 py-10">
      <p className="text-gray-500">Načítám…</p>
    </div>
  );
}

function Shell(): React.ReactElement {
  const { user } = useAuth();
  return (
    <AlertsProvider user={user}>
      <Layout>
        <Suspense fallback={<Loading />}>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/hledat" element={<Search />} />
            <Route path="/inzerat/:id" element={<ListingDetail />} />
            <Route path="/inzerat/:id/upravit" element={<EditListing />} />
            <Route path="/prihlaseni" element={<Login />} />
            <Route path="/registrace" element={<Register />} />
            <Route path="/prodat" element={<Sell />} />
            <Route path="/ucet" element={<Account />} />
            {/* Původní cesta /seller-info byla přesunuta do sekce v účtu. */}
            <Route path="/seller-info" element={<Navigate to="/ucet" replace />} />
            <Route path="/obchody" element={<Deals />} />
            <Route path="/oblibene" element={<Favorites />} />
            <Route path="/profil/:id" element={<Profile />} />
            <Route path="/chat" element={<Chat />} />
            <Route path="/chat/:id" element={<Chat />} />
            <Route path="/admin" element={<Admin />} />
            <Route path="/admin/moderation" element={<Admin section="moderation" />} />
            <Route path="/admin/disputes" element={<Admin section="disputes" />} />
            <Route path="/admin/verifikace" element={<Admin section="verifications" />} />
            <Route path="/admin/uzivatele" element={<Admin section="users" />} />
            <Route path="/admin/audit" element={<Admin section="audit" />} />
            <Route path="/admin/dac7" element={<Admin section="dac7" />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </Layout>
    </AlertsProvider>
  );
}

export default function App(): React.ReactElement {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Shell />
      </BrowserRouter>
    </AuthProvider>
  );
}