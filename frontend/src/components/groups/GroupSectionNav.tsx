import Image from "next/image";
import Link from "next/link";

type GroupSection = "expenses" | "balances" | "settings";

type GroupSectionNavProps = {
  activeSection: GroupSection;
  groupId: string;
};

const sections: {
  id: GroupSection | "statistics";
  label: string;
  desktopIcon: string;
  mobileIcon?: string;
}[] = [
  {
    id: "expenses",
    label: "Expenses",
    desktopIcon: "/group-detail/expenses-desktop.svg",
    mobileIcon: "/group-detail/expenses-mobile.svg",
  },
  {
    id: "balances",
    label: "Balances",
    desktopIcon: "/group-detail/balances-desktop.svg",
    mobileIcon: "/group-detail/balances-mobile.svg",
  },
  {
    id: "statistics",
    label: "Statistics",
    desktopIcon: "/group-detail/statistics-desktop.svg",
    mobileIcon: "/group-detail/statistics-mobile.svg",
  },
  {
    id: "settings",
    label: "Settings",
    desktopIcon: "/group-detail/settings-desktop.svg",
  },
];

export default function GroupSectionNav({
  activeSection,
  groupId,
}: GroupSectionNavProps) {
  const groupPath = `/groups/${encodeURIComponent(groupId)}`;

  return (
    <nav aria-label="Group sections" className="group-detail__tabs">
      {sections.map((section) => {
        const isActive = section.id === activeSection;
        const className = [
          "group-detail__tab",
          isActive && "group-detail__tab--active",
          section.id === "settings" && "group-detail__tab--settings",
        ]
          .filter(Boolean)
          .join(" ");
        const content = (
          <>
            <Image
              alt=""
              className="group-detail__tab-icon group-detail__tab-icon--desktop"
              height={30}
              src={section.desktopIcon}
              unoptimized
              width={30}
            />
            {section.mobileIcon && (
              <Image
                alt=""
                className="group-detail__tab-icon group-detail__tab-icon--mobile"
                height={24}
                src={section.mobileIcon}
                unoptimized
                width={24}
              />
            )}
            <span>{section.label}</span>
          </>
        );

        if (section.id === "statistics") {
          return (
            <span
              aria-disabled="true"
              className={`${className} group-detail__tab--unavailable`}
              key={section.id}
            >
              {content}
            </span>
          );
        }

        const href =
          section.id === "expenses"
            ? groupPath
            : `${groupPath}/${section.id}`;

        return isActive ? (
          <span
            aria-current="page"
            className={className}
            key={section.id}
          >
            {content}
          </span>
        ) : (
          <Link className={className} href={href} key={section.id}>
            {content}
          </Link>
        );
      })}
    </nav>
  );
}
