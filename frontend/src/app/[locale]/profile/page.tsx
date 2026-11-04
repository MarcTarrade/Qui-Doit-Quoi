"use client";

import Image from "next/image";
import { Link } from "@/i18n/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState, type FormEvent } from "react";

import SiteHeader from "@/components/auth/SiteHeader";
import { apiImageUrl, apiRequest, apiRequestWithCsrf } from "@/lib/api";
import type { AuthUser } from "@/lib/auth-types";

type LoadState = "loading" | "ready" | "error" | "unauthenticated";

function isProfile(value: unknown): value is AuthUser {
  return (
    typeof value === "object" &&
    value !== null &&
    "id" in value &&
    typeof value.id === "string" &&
    "username" in value &&
    typeof value.username === "string" &&
    "firstname" in value &&
    typeof value.firstname === "string" &&
    "lastname" in value &&
    typeof value.lastname === "string" &&
    "email" in value &&
    typeof value.email === "string" &&
    "img" in value &&
    (value.img === null || typeof value.img === "string")
  );
}

export default function ProfilePage() {
  const t = useTranslations("Profile");
  const commonT = useTranslations("Common");
  const [profile, setProfile] = useState<AuthUser | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isRemovingImage, setIsRemovingImage] = useState(false);
  const [saved, setSaved] = useState(false);
  const [imageError, setImageError] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest("/api/users/me", {
      cache: "no-store",
      signal: controller.signal,
    })
      .then(async (response) => {
        if (response.status === 401) {
          setLoadState("unauthenticated");
          return;
        }
        if (!response.ok) {
          setLoadError(t("loadFailed", { status: response.status }));
          setLoadState("error");
          return;
        }
        const result: unknown = await response.json();
        if (!isProfile(result)) {
          throw new Error(t("invalidProfile"));
        }
        setProfile(result);
        setLoadState("ready");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setLoadError(
          error instanceof Error && error.message === t("invalidProfile")
            ? error.message
            : commonT("unexpectedError"),
        );
        setLoadState("error");
      });

    return () => controller.abort();
  }, [commonT, t]);

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profile) return;

    const data = new FormData(event.currentTarget);
    const payload = {
      username: String(data.get("username") ?? "").trim(),
      firstname: String(data.get("firstname") ?? "").trim(),
      lastname: String(data.get("lastname") ?? "").trim(),
      email: String(data.get("email") ?? "").trim(),
    };

    setSaveError("");
    setSaved(false);
    setIsSaving(true);
    try {
      const response = await apiRequestWithCsrf(
        `/api/users/${encodeURIComponent(profile.id)}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      );
      if (!response.ok) {
        setSaveError(t("saveFailed", { status: response.status }));
        return;
      }

      const result: unknown = await response.json();
      if (!isProfile(result)) {
        throw new Error(t("invalidProfile"));
      }
      setProfile(result);
      window.dispatchEvent(new CustomEvent("auth-user-updated", { detail: result }));
      setSaved(true);
    } catch (error) {
      setSaveError(
        error instanceof Error && error.message === t("invalidProfile")
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function uploadImage() {
    const image = fileInputRef.current?.files?.[0];
    if (!profile || !image) {
      setImageError(t("chooseImage"));
      return;
    }

    if (!["image/png", "image/jpeg"].includes(image.type)) {
      setImageError(t("invalidImageType"));
      return;
    }
    if (image.size > 5 * 1024 * 1024) {
      setImageError(t("imageTooLarge"));
      return;
    }

    const formData = new FormData();
    formData.set("image", image);
    setImageError("");
    setIsUploading(true);
    try {
      const response = await apiRequestWithCsrf(
        `/api/users/${encodeURIComponent(profile.id)}/image`,
        { method: "PUT", body: formData },
      );
      if (!response.ok) {
        setImageError(t("uploadFailed", { status: response.status }));
        return;
      }

      const result: unknown = await response.json();
      if (!isProfile(result)) {
        throw new Error(t("invalidProfile"));
      }
      setProfile(result);
      window.dispatchEvent(new CustomEvent("auth-user-updated", { detail: result }));
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch (error) {
      setImageError(
        error instanceof Error && error.message === t("invalidProfile")
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setIsUploading(false);
    }
  }

  async function removeImage() {
    if (!profile) return;
    setImageError("");
    setIsRemovingImage(true);
    try {
      const response = await apiRequestWithCsrf(
        `/api/users/${encodeURIComponent(profile.id)}/image`,
        { method: "DELETE" },
      );
      if (!response.ok) {
        setImageError(t("removeImageFailed", { status: response.status }));
        return;
      }
      const result: unknown = await response.json();
      if (!isProfile(result)) {
        throw new Error(t("invalidProfile"));
      }
      setProfile(result);
      window.dispatchEvent(new CustomEvent("auth-user-updated", { detail: result }));
    } catch (error) {
      setImageError(
        error instanceof Error && error.message === t("invalidProfile")
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setIsRemovingImage(false);
    }
  }

  const previewUrl = apiImageUrl(profile?.img ?? null);

  return (
    <main className="profile-page">
      <SiteHeader />
      <div className="profile-page__container">
        <Link className="profile-page__back" href="/">
          <Image alt="" height={20} src="/group-detail/back-mobile.svg" width={20} />
          {commonT("backToGroups")}
        </Link>
        <header className="profile-page__heading">
          <p>{t("eyebrow")}</p>
          <h1>{t("title")}</h1>
          <span>{t("description")}</span>
        </header>

        {loadState === "loading" && (
          <p className="profile-page__status" role="status">{t("loading")}</p>
        )}
        {loadState === "error" && (
          <div className="profile-page__message" role="alert">
            <p>{loadError}</p>
            <button onClick={() => window.location.reload()} type="button">
              {commonT("tryAgain")}
            </button>
          </div>
        )}
        {loadState === "unauthenticated" && (
          <div className="profile-page__message">
            <p>{t("signInRequired")}</p>
            <Link href="/login">{t("signIn")}</Link>
          </div>
        )}
        {loadState === "ready" && profile && (
          <form className="profile-card" onSubmit={saveProfile}>
            <section aria-label={t("picture")} className="profile-card__picture">
              <div className="profile-card__avatar">
                {previewUrl ? (
                  <Image alt="" fill sizes="112px" src={previewUrl} unoptimized />
                ) : (
                  <span>{profile.firstname.charAt(0).toUpperCase()}</span>
                )}
              </div>
              <div className="profile-card__picture-copy">
                <h2>{t("picture")}</h2>
                <p>{t("pictureHint")}</p>
                <label className="profile-card__field">
                  <span>{t("chooseImage")}</span>
                  <input
                    accept="image/png,image/jpeg"
                    aria-label={t("chooseImage")}
                    className="profile-card__file"
                    ref={fileInputRef}
                    type="file"
                  />
                </label>
                <p>{t("imageHint")}</p>
                <div className="profile-card__image-actions">
                  <button
                    className="profile-card__image-button"
                    disabled={isUploading || isRemovingImage}
                    onClick={() => void uploadImage()}
                    type="button"
                  >
                    {isUploading ? t("uploading") : t("uploadImage")}
                  </button>
                  {profile.img && (
                    <button
                      className="profile-card__remove-button"
                      disabled={isUploading || isRemovingImage}
                      onClick={() => void removeImage()}
                      type="button"
                    >
                      {isRemovingImage ? t("removingImage") : t("removeImage")}
                    </button>
                  )}
                </div>
              </div>
            </section>

            <section className="profile-card__details">
              <h2>{t("personalInformation")}</h2>
              <div className="profile-card__fields">
                <label className="profile-card__field">
                  <span>{t("username")}</span>
                  <input
                    autoComplete="username"
                    defaultValue={profile.username}
                    maxLength={50}
                    name="username"
                    required
                  />
                </label>
                <label className="profile-card__field">
                  <span>{t("firstName")}</span>
                  <input
                    autoComplete="given-name"
                    defaultValue={profile.firstname}
                    maxLength={100}
                    name="firstname"
                    required
                  />
                </label>
                <label className="profile-card__field">
                  <span>{t("lastName")}</span>
                  <input
                    autoComplete="family-name"
                    defaultValue={profile.lastname}
                    maxLength={100}
                    name="lastname"
                    required
                  />
                </label>
                <label className="profile-card__field">
                  <span>{t("email")}</span>
                  <input
                    autoComplete="email"
                    defaultValue={profile.email}
                    maxLength={254}
                    name="email"
                    required
                    type="email"
                  />
                </label>
              </div>
            </section>

            {saveError && <p className="profile-card__error" role="alert">{saveError}</p>}
            {imageError && <p className="profile-card__error" role="alert">{imageError}</p>}
            {saved && <p className="profile-card__success" role="status">{t("saved")}</p>}
            <button className="profile-card__submit" disabled={isSaving} type="submit">
              {isSaving ? t("saving") : t("save")}
            </button>
          </form>
        )}
      </div>
    </main>
  );
}
