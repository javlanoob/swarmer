package com.swarmer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SwarmerFont
{
	RUNESCAPE("RuneScape"),
	ARIAL("Arial"),
	CAMBRIA("Cambria"),
	ROCKWELL("Rockwell"),
	SEGOE_UI("Segoe UI"),
	TIMES_NEW_ROMAN("Times New Roman"),
	VERDANA("Verdana"),
	DIALOG("Dialog");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
