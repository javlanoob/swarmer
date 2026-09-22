package com.swarmer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum HighlightMode
{
	TRUE_TILE("True tile"),
	TILE("Tile"),
	HULL("Hull"),
	OUTLINE("Outline");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
